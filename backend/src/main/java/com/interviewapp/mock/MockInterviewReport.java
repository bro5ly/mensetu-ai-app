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
 * 本番模擬面接1回分のレポート(本番1回=1レコード)。
 * セッション終了後にバックグラウンドで1回のLLM呼び出しにまとめて生成する
 * ({@code MockSessionService.generateAndSaveReport}参照。質問ごとの並列評価はしない)。
 */
@Entity
@Table(name = "mock_interview_reports")
@Getter
@Setter
@NoArgsConstructor
public class MockInterviewReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    /** 0〜100点のスコア。 */
    @Column(nullable = false)
    private int score;

    @Column(name = "answer_tendency_analysis", nullable = false, columnDefinition = "text")
    private String answerTendencyAnalysis;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public MockInterviewReport(UUID sessionId, int score, String answerTendencyAnalysis) {
        this.sessionId = sessionId;
        this.score = score;
        this.answerTendencyAnalysis = answerTendencyAnalysis;
    }
}
