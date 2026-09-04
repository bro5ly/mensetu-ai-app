package com.interviewapp.practice;

import com.interviewapp.session.MessageType;

/**
 * 1ターンのアシスタント応答の進行を受け取るコールバック。
 * WebSocket ハンドラが実装し、{@code assistant_message_start / assistant_text_chunk / assistant_message_end}
 * イベントとして送出する。
 */
public interface PracticeTurnListener {

    /** マーカー判定が済み、応答本文のストリーミングを開始するとき。 */
    void onAssistantStart(MessageType messageType);

    /** マーカー除去済みの本文チャンク。 */
    void onAssistantChunk(String textChunk);

    /** 応答が完了したとき。{@code fullContent} はマーカー除去済みの全文。 */
    void onAssistantEnd(MessageType messageType, String fullContent);
}
