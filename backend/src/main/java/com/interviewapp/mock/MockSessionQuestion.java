package com.interviewapp.mock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 本番セッションが対象とする質問(常に1件、{@code displayOrder}は0固定)。
 * 会社に紐づく静的な {@code interview_questions} とは別テーブルだが、本番モードは
 * 質問ごとに独立した一問一答形式(CLAUDE.md参照)のため、その質問の文面・内部カテゴリを
 * セッション開始時にそのままコピーするだけで、LLM生成は行わない({@code MockSessionService}参照)。
 */
@Entity
@Table(name = "mock_session_questions")
@Getter
@Setter
@NoArgsConstructor
public class MockSessionQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    @Column(name = "internal_category", length = 50)
    private String internalCategory;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public MockSessionQuestion(UUID sessionId, String questionText, String internalCategory, int displayOrder) {
        this.sessionId = sessionId;
        this.questionText = questionText;
        this.internalCategory = internalCategory;
        this.displayOrder = displayOrder;
    }
}
