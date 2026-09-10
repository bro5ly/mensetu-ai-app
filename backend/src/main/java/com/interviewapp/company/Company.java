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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 会社。サイドバーでは会社の直下に具体的な質問が並ぶ。
 * 企業リサーチ由来の項目（culture_keywords 等）は本フェーズでは扱わない。
 */
@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String overview;

    /**
     * 会社に紐づかない「汎用的な質問」を表す特別な会社かどうか。既存の質問バンク
     * (question_bank_entries)を複製した1行だけがtrueになる({@code V7__add_generic_question_set.sql}
     * 参照)。practice/mockの既存の仕組みをそのまま再利用するための特別扱いで、通常の会社一覧
     * ({@code CompanyService#list}) からは除外し、専用の {@code GET /api/companies/generic} で取得する。
     */
    @Column(name = "is_generic", nullable = false)
    private boolean generic;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Company(String name, String overview) {
        this.name = name;
        this.overview = overview;
    }
}
