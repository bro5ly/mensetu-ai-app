package com.interviewapp.chat;

import com.interviewapp.common.NotFoundException;
import com.interviewapp.mock.MockSessionService;
import com.interviewapp.mock.MockTurnListener;
import com.interviewapp.mock.MockTurnService;
import com.interviewapp.practice.PracticeCoachException;
import com.interviewapp.practice.PracticeTurnListener;
import com.interviewapp.practice.PracticeTurnService;
import com.interviewapp.practice.SpeechMetrics;
import com.interviewapp.practice.SpeechMetricsAnalyzer;
import com.interviewapp.session.ChatSession;
import com.interviewapp.session.MessageType;
import com.interviewapp.session.SessionMode;
import com.interviewapp.session.SessionService;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

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
    private static final String ATTR_SESSION_MODE = "chatSessionMode";
    private static final String ATTR_AUDIO = "audioBuffer";
    private static final String ATTR_AUDIO_CONTENT_TYPE = "audioContentType";
    private static final String ATTR_OUTBOUND = "outboundSession";
    private static final String ATTR_PARTIAL_IN_PROGRESS = "partialTranscriptInProgress";

    /** 送信キューが詰まった場合に諦めるまでの時間・バッファ上限(ConcurrentWebSocketSessionDecorator用)。 */
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 1024 * 1024;

    private final PracticeTurnService turnService;
    private final MockTurnService mockTurnService;
    private final MockSessionService mockSessionService;
    private final SessionService sessionService;
    private final SttClient sttClient;
    private final TtsClient ttsClient;
    private final TtsCache ttsCache;
    private final WsEventCodec codec;
    private final int voicevoxSpeakerId;

    /**
     * {@code audio_query}(軽い処理)専用のプール。文が確定した時点ですぐに投げて、
     * 他の文の処理やLLMのテキスト生成と並行して進める。
     */
    private final ExecutorService audioQueryExecutor = Executors.newFixedThreadPool(3);

    /**
     * {@code synthesis}(重い処理)専用の単一スレッドのプール。ローカルの単一VOICEVOXエンジンに
     * 同時に何件も投げると効率が落ちるため、意図的に直列化する。audio_queryを先に済ませておく
     * ことで、この直列区間に入る頃には多くの場合audio_queryが完了済みになっている。
     */
    private final ExecutorService synthesisExecutor = Executors.newSingleThreadExecutor();

    /**
     * 録音中のプレビュー文字起こし({@code request_partial_transcript})専用のプール。
     * WSのテキストメッセージ処理はセッションごとに直列なので、ここでSTT呼び出しを
     * 別スレッドに逃がさないと、その間 audio_chunk の受信が滞ってしまう。
     */
    private final ExecutorService partialTranscriptExecutor = Executors.newFixedThreadPool(2);

    public PracticeWebSocketHandler(PracticeTurnService turnService,
                                    MockTurnService mockTurnService,
                                    MockSessionService mockSessionService,
                                    SessionService sessionService,
                                    SttClient sttClient,
                                    TtsClient ttsClient,
                                    TtsCache ttsCache,
                                    WsEventCodec codec,
                                    @Value("${app.voicevox.speaker:3}") int voicevoxSpeakerId) {
        this.turnService = turnService;
        this.mockTurnService = mockTurnService;
        this.mockSessionService = mockSessionService;
        this.sessionService = sessionService;
        this.sttClient = sttClient;
        this.ttsClient = ttsClient;
        this.ttsCache = ttsCache;
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
        ChatSession chatSession;
        try {
            chatSession = sessionService.findOrThrow(chatSessionId);
        } catch (NotFoundException e) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("セッションが存在しません"));
            return;
        }
        session.getAttributes().put(ATTR_SESSION_ID, chatSessionId);
        session.getAttributes().put(ATTR_SESSION_MODE, chatSession.getMode());
        session.getAttributes().put(ATTR_AUDIO, new ByteArrayOutputStream());
        // テキスト送信(呼び出しスレッド)と音声送信(TTS実行スレッド)が同一セッションへ
        // 同時に書き込みうるため、スレッドセーフな送信用にラップしておく。
        session.getAttributes().put(ATTR_OUTBOUND,
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES));

        // MOCKモードは面接官から話し始める(候補者はまだ何も答えていない)。既に開始済み
        // (READYでなくなっている。再接続等)なら MockTurnService.startInterview 側で何もしない。
        if (chatSession.getMode() == SessionMode.MOCK) {
            try {
                runMockOpening(session, chatSessionId);
            } catch (PracticeCoachException e) {
                log.warn("本番面接官 LLM の呼び出しに失敗: {}", e.getMessage());
                send(session, codec.error(e.getMessage()));
            } catch (RuntimeException e) {
                log.error("本番面接の開始でエラー", e);
                send(session, codec.error("面接の開始に失敗しました"));
            }
        }
    }

    @PreDestroy
    void shutdown() {
        audioQueryExecutor.shutdown();
        synthesisExecutor.shutdown();
        partialTranscriptExecutor.shutdown();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UUID chatSessionId = (UUID) session.getAttributes().get(ATTR_SESSION_ID);
        WsEventCodec.Inbound inbound = codec.parseInbound(message.getPayload());

        try {
            switch (String.valueOf(inbound.type())) {
                case WsProtocol.USER_TEXT -> {
                    if (inbound.text() != null && !inbound.text().isBlank()) {
                        String text = inbound.text().trim();
                        if (isMock(session)) {
                            runMockTurn(session, chatSessionId, text);
                        } else {
                            // テキスト入力には音声が無いため話し方の参考情報は無し(null)。
                            runTurn(session, chatSessionId, text, null);
                        }
                    }
                }
                case WsProtocol.END_TURN -> handleEndTurn(session, chatSessionId);
                case WsProtocol.FORCE_END -> handleForceEnd(session, chatSessionId);
                case WsProtocol.REQUEST_PARTIAL_TRANSCRIPT -> handlePartialTranscriptRequest(session);
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
        TranscriptionResult result = sttClient.transcribe(audio, contentType);
        String transcript = result.text();
        if (transcript == null || transcript.isBlank()) {
            send(session, codec.error("音声を認識できませんでした。もう一度話してみてください。"));
            return;
        }
        send(session, codec.transcript(transcript));

        if (isMock(session)) {
            runMockTurn(session, chatSessionId, transcript);
            return;
        }
        // 話速・フィラーワード・間はLLMへの参考情報としてのみ使う(ユーザーには見せない、
        // 上のtranscriptイベント/chat_messagesには影響しない)。タイムスタンプが取れない
        // STTサーバーの場合はSpeechMetricsAnalyzer.analyzeがnullを返し、単に分析をスキップする。
        SpeechMetrics speechMetrics =
                SpeechMetricsAnalyzer.analyze(transcript, result.durationSeconds(), result.longestGapSeconds());
        runTurn(session, chatSessionId, transcript, speechMetrics);
    }

    private boolean isMock(WebSocketSession session) {
        return session.getAttributes().get(ATTR_SESSION_MODE) == SessionMode.MOCK;
    }

    /**
     * 録音継続中に、その時点までの音声だけを取り出してプレビュー文字起こしする。
     * 録音バッファ自体はリセットしない({@code end_turn} で使う本番の文字起こしに影響させないため)。
     * 前回のプレビュー文字起こしがまだ完了していなければ、今回のリクエストは無視する
     * (フロントは一定間隔で送り続けるので、次のタイミングでまた送られてくる)。
     */
    private void handlePartialTranscriptRequest(WebSocketSession session) {
        AtomicBoolean inProgress = (AtomicBoolean) session.getAttributes()
                .computeIfAbsent(ATTR_PARTIAL_IN_PROGRESS, k -> new AtomicBoolean(false));
        if (!inProgress.compareAndSet(false, true)) {
            return;
        }

        ByteArrayOutputStream buffer = (ByteArrayOutputStream) session.getAttributes().get(ATTR_AUDIO);
        byte[] audio = buffer.toByteArray();
        if (audio.length == 0) {
            inProgress.set(false);
            return;
        }
        String contentType = (String) session.getAttributes().getOrDefault(ATTR_AUDIO_CONTENT_TYPE, "audio/webm");

        partialTranscriptExecutor.submit(() -> {
            try {
                String transcript = sttClient.transcribe(audio, contentType).text();
                if (transcript != null && !transcript.isBlank()) {
                    send(session, codec.partialTranscript(transcript));
                }
            } finally {
                inProgress.set(false);
            }
        });
    }

    private void handleForceEnd(WebSocketSession session, UUID chatSessionId) throws IOException {
        if (isMock(session)) {
            // レポート生成は非同期でトリガーされる(結果は GET /api/sessions/{id}/report で取得する)。
            // フロントは通常このWSメッセージとほぼ同時にREST側の /end も呼ぶため、二重トリガーに
            // なりうるが MockSessionService.triggerReportGeneration は冪等なので安全。
            mockSessionService.endByUser(chatSessionId);
        } else {
            sessionService.endByUser(chatSessionId);
        }
        send(session, codec.sessionEnded("USER_ENDED"));
        session.close(CloseStatus.NORMAL);
    }

    /**
     * ユーザー1ターン分を処理する。LLM のテキストストリームを受け取りながら、
     * 句点等で区切りが確定した文から順に非同期でTTS合成・送信をキューイングする。
     * これによりテキスト生成の完了を待たずに音声再生を始められ、
     * かつ1セッション内では文の送信順序が保たれる（直前の文の合成・送信完了後に次を開始）。
     */
    private void runTurn(WebSocketSession session, UUID chatSessionId, String userText, SpeechMetrics speechMetrics) {
        StringBuilder ttsBuffer = new StringBuilder();
        AtomicReference<CompletableFuture<Void>> ttsChain =
                new AtomicReference<>(CompletableFuture.completedFuture(null));

        PracticeTurnListener listener = new PracticeTurnListener() {
            @Override
            public void onAssistantStart(MessageType messageType) {
                send(session, codec.assistantMessageStart(messageType));
            }

            @Override
            public void onAssistantChunk(String textChunk) {
                send(session, codec.assistantTextChunk(textChunk));

                ttsBuffer.append(textChunk);
                SentenceSplitter.Result result = SentenceSplitter.extract(ttsBuffer.toString());
                if (!result.sentences().isEmpty()) {
                    ttsBuffer.setLength(0);
                    ttsBuffer.append(result.remainder());
                    result.sentences().forEach(sentence -> queueTts(session, ttsChain, sentence));
                }
            }

            @Override
            public void onAssistantEnd(MessageType messageType, String fullContent) {
                queueTts(session, ttsChain, ttsBuffer.toString());
                ttsChain.get().whenComplete((v, e) -> send(session, codec.assistantMessageEnd()));
            }
        };
        turnService.handleUserTurn(chatSessionId, userText, speechMetrics, listener);
    }

    /** 本番模擬面接の冒頭、面接官から挨拶と最初の質問を発話させる。 */
    private void runMockOpening(WebSocketSession session, UUID chatSessionId) {
        mockTurnService.startInterview(chatSessionId, buildMockTurnListener(session, chatSessionId));
    }

    /** 本番模擬面接の1ターン(候補者の回答 → 面接官の応答)を処理する。 */
    private void runMockTurn(WebSocketSession session, UUID chatSessionId, String userText) {
        mockTurnService.handleUserTurn(chatSessionId, userText, buildMockTurnListener(session, chatSessionId));
    }

    /**
     * 本番模擬面接用の{@link MockTurnListener}を組み立てる。TTSキューイングの仕組みは
     * practice モードの{@code runTurn}と同じ({@link SentenceSplitter}で文単位に区切って
     * {@link #queueTts}へ渡す)。質問の切り替わり・面接終了は、その時点までの音声送信
     * ({@code ttsChain})が終わってからイベントを送る(音声が先、進捗更新は後)。
     */
    private MockTurnListener buildMockTurnListener(WebSocketSession session, UUID chatSessionId) {
        StringBuilder ttsBuffer = new StringBuilder();
        AtomicReference<CompletableFuture<Void>> ttsChain =
                new AtomicReference<>(CompletableFuture.completedFuture(null));

        return new MockTurnListener() {
            @Override
            public void onAssistantStart() {
                send(session, codec.assistantMessageStart(MessageType.NORMAL));
            }

            @Override
            public void onAssistantChunk(String textChunk) {
                send(session, codec.assistantTextChunk(textChunk));

                ttsBuffer.append(textChunk);
                SentenceSplitter.Result result = SentenceSplitter.extract(ttsBuffer.toString());
                if (!result.sentences().isEmpty()) {
                    ttsBuffer.setLength(0);
                    ttsBuffer.append(result.remainder());
                    result.sentences().forEach(sentence -> queueTts(session, ttsChain, sentence));
                }
            }

            @Override
            public void onAssistantEnd(String fullContent) {
                queueTts(session, ttsChain, ttsBuffer.toString());
                ttsChain.get().whenComplete((v, e) -> send(session, codec.assistantMessageEnd()));
            }

            @Override
            public void onQuestionAdvance(String nextQuestionText, int questionIndex, int totalQuestions) {
                ttsChain.get().whenComplete((v, e) ->
                        send(session, codec.mockQuestionAdvanced(nextQuestionText, questionIndex, totalQuestions)));
            }

            @Override
            public void onInterviewEnd() {
                ttsChain.get().whenComplete((v, e) -> {
                    send(session, codec.sessionEnded("AI_JUDGED"));
                    mockSessionService.triggerReportGeneration(chatSessionId).whenComplete((v2, e2) -> {
                        send(session, codec.reportReady());
                        closeQuietly(session);
                    });
                });
            }
        };
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        } catch (IOException e) {
            log.warn("WS クローズに失敗しました", e);
        }
    }

    /**
     * 1文分の送信を、そのセッションの既存キューの後ろに直列でつなぐ(送信順序を保証するため)。
     * ただし実際のTTS準備({@link #prepareAudio})はここで待たず即座に開始しており、
     * 前の文の送信を待っている間も並行して進む。
     */
    private void queueTts(WebSocketSession session, AtomicReference<CompletableFuture<Void>> ttsChain, String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        CompletableFuture<byte[]> audioReady = prepareAudio(trimmed);
        ttsChain.updateAndGet(previous -> previous
                .thenCompose(v -> audioReady)
                .thenAccept(audio -> sendAudio(session, audio)));
    }

    /**
     * 1文分の音声を用意する。キャッシュにあればそれを使い、無ければ audio_query→synthesis の
     * 2段階で合成してキャッシュに積む。audio_query は{@link #audioQueryExecutor}(複数スレッド)
     * ですぐに開始し、synthesis は{@link #synthesisExecutor}(単一スレッド)で直列化する。
     */
    private CompletableFuture<byte[]> prepareAudio(String text) {
        byte[] cached = ttsCache.get(text, voicevoxSpeakerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture
                .supplyAsync(() -> ttsClient.createAudioQuery(text, voicevoxSpeakerId), audioQueryExecutor)
                .thenApplyAsync(query -> {
                    byte[] audio = ttsClient.synthesizeFromQuery(query, voicevoxSpeakerId);
                    ttsCache.put(text, voicevoxSpeakerId, audio);
                    return audio;
                }, synthesisExecutor)
                .exceptionally(e -> {
                    log.warn("TTS 音声合成に失敗しました（テキストは送信済み）", e);
                    return null;
                });
    }

    private void sendAudio(WebSocketSession session, byte[] audio) {
        try {
            WebSocketSession outbound = outbound(session);
            if (audio != null && audio.length > 0 && outbound.isOpen()) {
                outbound.sendMessage(new BinaryMessage(audio));
            }
        } catch (IOException e) {
            log.warn("TTS 音声の送信に失敗しました", e);
        }
    }

    private void send(WebSocketSession session, String json) {
        try {
            WebSocketSession outbound = outbound(session);
            if (outbound.isOpen()) {
                outbound.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.warn("WS 送信に失敗しました", e);
        }
    }

    /** テキスト送信スレッドと音声送信スレッドが競合しないよう、スレッドセーフなラッパーを返す。 */
    private WebSocketSession outbound(WebSocketSession session) {
        Object decorated = session.getAttributes().get(ATTR_OUTBOUND);
        return decorated instanceof WebSocketSession ws ? ws : session;
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
