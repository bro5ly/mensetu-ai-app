package com.interviewapp.chat;

import com.interviewapp.common.NotFoundException;
import com.interviewapp.practice.PracticeCoachException;
import com.interviewapp.practice.PracticeTurnListener;
import com.interviewapp.practice.PracticeTurnService;
import com.interviewapp.session.MessageType;
import com.interviewapp.session.SessionService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * 練習モードのリアルタイム音声／テキストをさばく WebSocket ハンドラ。
 * エンドポイント: {@code /ws/sessions/{sessionId}}
 *
 * <p>薄く保ち、会話ロジックは {@link PracticeTurnService}、STT/TTS は各クライアントへ委譲する。</p>
 */
@Component
public class PracticeWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PracticeWebSocketHandler.class);
    private static final String ATTR_SESSION_ID = "chatSessionId";
    private static final String ATTR_AUDIO = "audioBuffer";
    private static final String ATTR_AUDIO_CONTENT_TYPE = "audioContentType";

    private final PracticeTurnService turnService;
    private final SessionService sessionService;
    private final SttClient sttClient;
    private final TtsClient ttsClient;
    private final WsEventCodec codec;
    private final int voicevoxSpeakerId;

    public PracticeWebSocketHandler(PracticeTurnService turnService,
                                    SessionService sessionService,
                                    SttClient sttClient,
                                    TtsClient ttsClient,
                                    WsEventCodec codec,
                                    @Value("${app.voicevox.speaker:3}") int voicevoxSpeakerId) {
        this.turnService = turnService;
        this.sessionService = sessionService;
        this.sttClient = sttClient;
        this.ttsClient = ttsClient;
        this.codec = codec;
        this.voicevoxSpeakerId = voicevoxSpeakerId;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        UUID chatSessionId = parseSessionId(session);
        if (chatSessionId == null) {
            session.close(CloseStatus.BAD_DATA.withReason("session id が不正です"));
            return;
        }
        try {
            sessionService.findOrThrow(chatSessionId);
        } catch (NotFoundException e) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("セッションが存在しません"));
            return;
        }
        session.getAttributes().put(ATTR_SESSION_ID, chatSessionId);
        session.getAttributes().put(ATTR_AUDIO, new ByteArrayOutputStream());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UUID chatSessionId = (UUID) session.getAttributes().get(ATTR_SESSION_ID);
        WsEventCodec.Inbound inbound = codec.parseInbound(message.getPayload());

        try {
            switch (String.valueOf(inbound.type())) {
                case WsProtocol.USER_TEXT -> {
                    if (inbound.text() != null && !inbound.text().isBlank()) {
                        runTurn(session, chatSessionId, inbound.text().trim());
                    }
                }
                case WsProtocol.END_TURN -> handleEndTurn(session, chatSessionId);
                case WsProtocol.FORCE_END -> handleForceEnd(session, chatSessionId);
                default -> log.debug("未知の WS メッセージ type={}", inbound.type());
            }
        } catch (PracticeCoachException e) {
            // メッセージはそのままフロントに出せる日本語（Ollama 未起動・モデル読み込み失敗など）
            log.warn("練習コーチ LLM の呼び出しに失敗: {}", e.getMessage());
            send(session, codec.error(e.getMessage()));
        } catch (RuntimeException e) {
            log.error("WS メッセージ処理でエラー", e);
            send(session, codec.error("処理中にエラーが発生しました"));
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ByteArrayOutputStream buffer = (ByteArrayOutputStream) session.getAttributes().get(ATTR_AUDIO);
        byte[] chunk = new byte[message.getPayload().remaining()];
        message.getPayload().get(chunk);
        buffer.write(chunk, 0, chunk.length);
    }

    private void handleEndTurn(WebSocketSession session, UUID chatSessionId) throws IOException {
        ByteArrayOutputStream buffer = (ByteArrayOutputStream) session.getAttributes().get(ATTR_AUDIO);
        byte[] audio = buffer.toByteArray();
        buffer.reset();
        if (audio.length == 0) {
            return;
        }
        String contentType = (String) session.getAttributes().getOrDefault(ATTR_AUDIO_CONTENT_TYPE, "audio/webm");
        String transcript = sttClient.transcribe(audio, contentType);
        if (transcript == null || transcript.isBlank()) {
            send(session, codec.error("音声を認識できませんでした。もう一度話してみてください。"));
            return;
        }
        send(session, codec.transcript(transcript));
        runTurn(session, chatSessionId, transcript);
    }

    private void handleForceEnd(WebSocketSession session, UUID chatSessionId) throws IOException {
        sessionService.endByUser(chatSessionId);
        send(session, codec.sessionEnded("USER_ENDED"));
        session.close(CloseStatus.NORMAL);
    }

    private void runTurn(WebSocketSession session, UUID chatSessionId, String userText) {
        PracticeTurnListener listener = new PracticeTurnListener() {
            @Override
            public void onAssistantStart(MessageType messageType) {
                send(session, codec.assistantMessageStart(messageType));
            }

            @Override
            public void onAssistantChunk(String textChunk) {
                send(session, codec.assistantTextChunk(textChunk));
            }

            @Override
            public void onAssistantEnd(MessageType messageType, String fullContent) {
                sendTts(session, fullContent);
                send(session, codec.assistantMessageEnd());
            }
        };
        turnService.handleUserTurn(chatSessionId, userText, listener);
    }

    private void sendTts(WebSocketSession session, String text) {
        try {
            byte[] audio = ttsClient.synthesize(text, voicevoxSpeakerId);
            if (audio != null && audio.length > 0 && session.isOpen()) {
                session.sendMessage(new BinaryMessage(audio));
            }
        } catch (RuntimeException | IOException e) {
            log.warn("TTS 音声の送信に失敗しました（テキストは送信済み）", e);
        }
    }

    private void send(WebSocketSession session, String json) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.warn("WS 送信に失敗しました", e);
        }
    }

    private static UUID parseSessionId(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        String path = session.getUri().getPath();
        String last = path.substring(path.lastIndexOf('/') + 1);
        try {
            return UUID.fromString(last);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
