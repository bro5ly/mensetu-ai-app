package com.interviewapp.profile;

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
 * ユーザープロフィール(履歴書のような参考情報)。
 *
 * <p>ローカル単一ユーザー前提のためログイン・複数ユーザーの概念は持たず、常に高々1行しか
 * 存在しない想定({@link UserProfileService}参照)。練習・本番モードの面接官プロンプトに
 * 「候補者情報」として渡し、面接官が候補者の背景を踏まえた質問を組み立てられるようにする。</p>
 */
@Entity
@Table(name = "user_profile")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "resume_text", columnDefinition = "text")
    private String resumeText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserProfile(String resumeText) {
        this.resumeText = resumeText;
    }
}
