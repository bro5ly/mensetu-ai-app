package com.interviewapp.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewapp.session.MessageType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * WebSocket のテキストフレーム（JSON）を組み立て／解釈する純粋なコーデック。
 * ハンドラから切り離してあり単体テストしやすい。
 */
@Component
public class WsEventCodec {

    private final ObjectMapper objectMapper;

    public WsEventCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 受信テキストをパースする。type が無い／不正な JSON なら {@code type=null} の結果を返す。 */
    public Inbound parseInbound(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("type").isTextual() ? node.get("type").asText() : null;
            String text = node.path("text").isTextual() ? node.get("text").asText() : null;
            return new Inbound(type, text);
        } catch (JsonProcessingException e) {
            return new Inbound(null, null);
        }
    }

    public String transcript(String text) {
        return write(Map.of("type", WsProtocol.TRANSCRIPT, "text", text));
    }

    public String partialTranscript(String text) {
        return write(Map.of("type", WsProtocol.PARTIAL_TRANSCRIPT, "text", text));
    }

    public String assistantMessageStart(MessageType messageType) {
        return write(Map.of("type", WsProtocol.ASSISTANT_MESSAGE_START, "messageType", messageType.name()));
    }

    public String assistantTextChunk(String text) {
        return write(Map.of("type", WsProtocol.ASSISTANT_TEXT_CHUNK, "text", text));
    }

    public String assistantMessageEnd() {
        return write(Map.of("type", WsProtocol.ASSISTANT_MESSAGE_END));
    }

    public String sessionEnded(String reason) {
        return write(Map.of("type", WsProtocol.SESSION_ENDED, "reason", reason));
    }

    public String error(String message) {
        return write(Map.of("type", WsProtocol.ERROR, "message", message));
    }

    /** MOCKのみ。次の質問に進んだことを伝える(進捗表示の更新用)。questionIndex/totalQuestionsは1始まり。 */
    public String mockQuestionAdvanced(String questionText, int questionIndex, int totalQuestions) {
        return write(Map.of(
                "type", WsProtocol.MOCK_QUESTION_ADVANCED,
                "questionText", questionText,
                "questionIndex", questionIndex,
                "totalQuestions", totalQuestions));
    }

    /** MOCKのみ。バックグラウンド生成していたレポートの準備ができた。 */
    public String reportReady() {
        return write(Map.of("type", WsProtocol.REPORT_READY));
    }

    private String write(Map<String, ?> fields) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(fields));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("WS イベントの JSON 変換に失敗しました", e);
        }
    }

    /** 受信メッセージ。 */
    public record Inbound(String type, String text) {
    }
}
