package com.interviewapp.session;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** セッション関連の DTO 群。 */
public final class SessionDtos {

    private SessionDtos() {
    }

    public record MessageResponse(
            UUID id,
            MessageRole role,
            String content,
            MessageType messageType,
            int sequenceNo,
            Instant createdAt) {

        public static MessageResponse from(ChatMessage m) {
            return new MessageResponse(m.getId(), m.getRole(), m.getContent(),
                    m.getMessageType(), m.getSequenceNo(), m.getCreatedAt());
        }
    }

    public record SessionDetail(
            UUID id,
            UUID companyId,
            UUID questionId,
            SessionMode mode,
            SessionStatus status,
            EndedReason endedReason,
            String lightSummary,
            Instant startedAt,
            Instant endedAt,
            Instant createdAt,
            List<MessageResponse> messages) {

        public static SessionDetail from(ChatSession s, List<ChatMessage> messages) {
            return new SessionDetail(
                    s.getId(), s.getCompanyId(), s.getQuestionId(), s.getMode(), s.getStatus(),
                    s.getEndedReason(), s.getLightSummary(), s.getStartedAt(), s.getEndedAt(), s.getCreatedAt(),
                    messages.stream().map(MessageResponse::from).toList());
        }
    }

    /** 練習セッション開始／再開のレスポンス。 */
    public record PracticeSessionResponse(
            UUID id,
            UUID companyId,
            UUID questionId,
            SessionStatus status,
            boolean resumed,
            Instant createdAt) {

        public static PracticeSessionResponse from(ChatSession s, boolean resumed) {
            return new PracticeSessionResponse(s.getId(), s.getCompanyId(), s.getQuestionId(),
                    s.getStatus(), resumed, s.getCreatedAt());
        }
    }

    /** 練習セッション終了（ユーザー強制終了）のレスポンス。 */
    public record PracticeEndResponse(UUID sessionId, int messageCount, String lightSummary) {
    }
}
