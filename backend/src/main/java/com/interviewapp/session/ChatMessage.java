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
 * 会話メッセージ。音声は保存せず、STT でテキスト化した結果のみを保存する。
 */
@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 10)
    private MessageType messageType = MessageType.NORMAL;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    /**
     * このメッセージがどの本番質問(mock_session_questions.display_order)への回答/深掘りだったか。
     * MOCKモードでのみ設定する(PRACTICEでは常にnull)。本番終了後のレポート生成で、
     * 質問ごとの回答をまとめて渡すために使う。
     */
    @Column(name = "question_order")
    private Integer questionOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ChatMessage(UUID sessionId, MessageRole role, String content, MessageType messageType, int sequenceNo) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.messageType = messageType;
        this.sequenceNo = sequenceNo;
    }
}
