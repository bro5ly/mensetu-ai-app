package com.interviewapp.company;

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
 * 会社に紐づかない、定番の面接質問と「良い回答の型」を保持するグローバルな参照データ。
 * 企業リサーチの質問生成が会社概要の言い回しだけに引っ張られて過度に狭くなるのを防ぐため、
 * {@link CompanyResearchPromptFactory} が手本としてLLMプロンプトに渡す。
 */
@Entity
@Table(name = "question_bank_entries")
@Getter
@Setter
@NoArgsConstructor
public class QuestionBankEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "internal_category", nullable = false, length = 50)
    private String internalCategory;

    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    @Column(name = "answer_guidance", nullable = false, columnDefinition = "text")
    private String answerGuidance;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public QuestionBankEntry(String internalCategory, String questionText, String answerGuidance) {
        this.internalCategory = internalCategory;
        this.questionText = questionText;
        this.answerGuidance = answerGuidance;
    }
}
