package com.interviewapp.chat;

/** WebSocket メッセージの type 値。クライアント／サーバー間で共有する語彙。 */
public final class WsProtocol {

    private WsProtocol() {
    }

    // クライアント → サーバー
    public static final String END_TURN = "end_turn";
    public static final String FORCE_END = "force_end";
    public static final String USER_TEXT = "user_text";

    // サーバー → クライアント
    public static final String TRANSCRIPT = "transcript";
    public static final String ASSISTANT_MESSAGE_START = "assistant_message_start";
    public static final String ASSISTANT_TEXT_CHUNK = "assistant_text_chunk";
    public static final String ASSISTANT_MESSAGE_END = "assistant_message_end";
    public static final String SESSION_ENDED = "session_ended";
    public static final String ERROR = "error";
}
