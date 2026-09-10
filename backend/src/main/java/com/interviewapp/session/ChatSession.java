package com.interviewapp.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * チャットセッション（練習 or 本番の共通基盤）。
 * 本番(MOCK)も質問ごとに独立した一問一答形式のため、practice と同じく question_id は
 * 常に対象の {@code interview_questions} を指す({@code mock_session_questions} はこの
 * セッション専用のコピーを1件だけ持つ)。
 */
@Entity
@Table(name = "chat_sessions")
@Getter
@Setter
@NoArgsConstructor
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "question_id")
    private UUID questionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SessionMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private SessionStatus status = SessionStatus.READY;

    @Enumerated(EnumType.STRING)
    @Column(name = "ended_reason", length = 15)
    private EndedReason endedReason;

    @Column(name = "light_summary", columnDefinition = "text")
    private String lightSummary;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 現在取り組んでいる質問の {@code mock_session_questions.display_order}(0始まり)。
     * MOCKモードでのみ使用する(PRACTICEでは既定値の0のまま無視される)。
     */
    @Column(name = "current_question_order", nullable = false)
    private int currentQuestionOrder = 0;

    /** 現在の質問で既に行ったフォローアップ(深掘り)の回数。MOCKモードでのみ使用する。 */
    @Column(name = "current_question_followups", nullable = false)
    private int currentQuestionFollowups = 0;

    public static ChatSession newPractice(UUID companyId, UUID questionId) {
        ChatSession s = new ChatSession();
        s.companyId = companyId;
        s.questionId = questionId;
        s.mode = SessionMode.PRACTICE;
        s.status = SessionStatus.READY;
        return s;
    }

    public static ChatSession newMock(UUID companyId, UUID questionId) {
        ChatSession s = new ChatSession();
        s.companyId = companyId;
        s.questionId = questionId;
        s.mode = SessionMode.MOCK;
        s.status = SessionStatus.READY;
        return s;
    }

    public boolean isEnded() {
        return status == SessionStatus.ENDED;
    }
}
