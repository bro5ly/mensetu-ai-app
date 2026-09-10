package com.interviewapp.chat;

/** WebSocket メッセージの type 値。クライアント／サーバー間で共有する語彙。 */
public final class WsProtocol {

    private WsProtocol() {
    }

    // クライアント → サーバー
    public static final String END_TURN = "end_turn";
    public static final String FORCE_END = "force_end";
    public static final String USER_TEXT = "user_text";
    /** 録音中に数秒おきに送られる、その時点までの音声のプレビュー文字起こしのリクエスト。 */
    public static final String REQUEST_PARTIAL_TRANSCRIPT = "request_partial_transcript";

    // サーバー → クライアント
    public static final String TRANSCRIPT = "transcript";
    /** {@link #REQUEST_PARTIAL_TRANSCRIPT} への応答。録音継続中の途中経過の文字起こし。 */
    public static final String PARTIAL_TRANSCRIPT = "partial_transcript";
    public static final String ASSISTANT_MESSAGE_START = "assistant_message_start";
    public static final String ASSISTANT_TEXT_CHUNK = "assistant_text_chunk";
    public static final String ASSISTANT_MESSAGE_END = "assistant_message_end";
    public static final String SESSION_ENDED = "session_ended";
    public static final String ERROR = "error";
    /** MOCKのみ。次の質問に進んだことをフロントに伝える(進捗表示の更新用)。 */
    public static final String MOCK_QUESTION_ADVANCED = "mock_question_advanced";
    /** MOCKのみ。セッション終了後、バックグラウンドで生成していたレポートの準備ができた。 */
    public static final String REPORT_READY = "report_ready";
}
