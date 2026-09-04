package com.interviewapp.session;

/**
 * 会話メッセージの種別。
 * PRACTICE の ASSISTANT 応答でのみ意味を持ち、それ以外は常に {@link #NORMAL}。
 */
public enum MessageType {
    NORMAL,
    ADVICE
}
