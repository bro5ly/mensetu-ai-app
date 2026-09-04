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
 * mode=MOCK のときは question_id が NULL。
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

    public static ChatSession newPractice(UUID companyId, UUID questionId) {
        ChatSession s = new ChatSession();
        s.companyId = companyId;
        s.questionId = questionId;
        s.mode = SessionMode.PRACTICE;
        s.status = SessionStatus.READY;
        return s;
    }

    public boolean isEnded() {
        return status == SessionStatus.ENDED;
    }
}
