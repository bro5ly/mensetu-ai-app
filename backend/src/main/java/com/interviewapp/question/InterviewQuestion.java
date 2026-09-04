package com.interviewapp.question;

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
 * 具体的な質問（サイドバーで会社の直下に並ぶもの）。
 * internal_category は UI にフォルダとして出さず、傾向分析の集計軸としてのみ持つ。
 */
@Entity
@Table(name = "interview_questions")
@Getter
@Setter
@NoArgsConstructor
public class InterviewQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    @Column(name = "internal_category", length = 50)
    private String internalCategory;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public InterviewQuestion(UUID companyId, String questionText, String internalCategory, int displayOrder) {
        this.companyId = companyId;
        this.questionText = questionText;
        this.internalCategory = internalCategory;
        this.displayOrder = displayOrder;
    }
}
