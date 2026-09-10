package com.interviewapp.mock;

import com.interviewapp.session.ChatSession;
import com.interviewapp.session.EndedReason;
import com.interviewapp.session.SessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 本番模擬面接関連の DTO 群。 */
public final class MockDtos {

    private MockDtos() {
    }

    public record FlowQuestionResponse(String questionText, String internalCategory, int displayOrder) {

        public static FlowQuestionResponse from(MockSessionQuestion q) {
            return new FlowQuestionResponse(q.getQuestionText(), q.getInternalCategory(), q.getDisplayOrder());
        }
    }

    /** 本番セッション開始のレスポンス。新規生成した質問フロー一式を含む。 */
    public record MockSessionStartResponse(
            UUID id, UUID companyId, SessionStatus status, List<FlowQuestionResponse> questions, Instant createdAt) {

        public static MockSessionStartResponse from(ChatSession s, List<MockSessionQuestion> questions) {
            return new MockSessionStartResponse(s.getId(), s.getCompanyId(), s.getStatus(),
                    questions.stream().map(FlowQuestionResponse::from).toList(), s.getCreatedAt());
        }
    }

    /** 過去の本番セッション一覧の1件(スコア推移表示用)。レポート未生成なら score は null。 */
    public record MockSessionSummaryResponse(
            UUID id,
            SessionStatus status,
            EndedReason endedReason,
            Integer score,
            Instant startedAt,
            Instant endedAt,
            Instant createdAt) {
    }

    /** ユーザー強制終了のレスポンス。レポートは非同期生成のため、この時点ではまだ確定しない。 */
    public record MockEndResponse(UUID sessionId, String status) {
    }

    public record QuestionFeedbackResponse(
            String questionText, String userAnswerSummary, String feedback, int sequenceNo) {

        public static QuestionFeedbackResponse from(MockQuestionFeedback f) {
            return new QuestionFeedbackResponse(
                    f.getQuestionText(), f.getUserAnswerSummary(), f.getFeedback(), f.getSequenceNo());
        }
    }

    public record MockReportResponse(
            UUID sessionId,
            int score,
            String answerTendencyAnalysis,
            List<QuestionFeedbackResponse> feedbacks,
            Instant createdAt) {
    }
}
