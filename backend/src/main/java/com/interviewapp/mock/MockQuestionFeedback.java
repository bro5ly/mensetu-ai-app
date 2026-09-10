package com.interviewapp.mock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** レポート内の質問ごとの具体的フィードバック。 */
@Entity
@Table(name = "mock_question_feedbacks")
@Getter
@Setter
@NoArgsConstructor
public class MockQuestionFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    @Column(name = "user_answer_summary", columnDefinition = "text")
    private String userAnswerSummary;

    @Column(nullable = false, columnDefinition = "text")
    private String feedback;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    public MockQuestionFeedback(
            UUID reportId, String questionText, String userAnswerSummary, String feedback, int sequenceNo) {
        this.reportId = reportId;
        this.questionText = questionText;
        this.userAnswerSummary = userAnswerSummary;
        this.feedback = feedback;
        this.sequenceNo = sequenceNo;
    }
}
