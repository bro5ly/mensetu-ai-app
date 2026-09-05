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
 * 会社に紐づくソース(ユーザーが登録したURLをサーバーがfetchして本文抽出したもの)。
 * NotebookLM のように、練習チャットの回答精度を上げる参考資料として使う。
 */
@Entity
@Table(name = "company_sources")
@Getter
@Setter
@NoArgsConstructor
public class CompanySource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @CreationTimestamp
    @Column(name = "fetched_at", nullable = false, updatable = false)
    private Instant fetchedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CompanySource(UUID companyId, String url, String title, String content) {
        this.companyId = companyId;
        this.url = url;
        this.title = title;
        this.content = content;
    }
}
