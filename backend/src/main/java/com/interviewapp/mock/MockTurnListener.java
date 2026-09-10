package com.interviewapp.mock;

/**
 * 本番模擬面接の1ターンの進行を受け取るコールバック。
 * WebSocket ハンドラが実装し、{@code assistant_message_start / assistant_text_chunk /
 * assistant_message_end} イベント(練習モードと共通のコーデック)に加え、質問の切り替わり・
 * 面接全体の終了を専用イベントとして送出する。
 */
public interface MockTurnListener {

    /** 応答本文のストリーミングを開始するとき。 */
    void onAssistantStart();

    /** 本文チャンク(マーカー除去済み)。 */
    void onAssistantChunk(String textChunk);

    /** 応答が完了したとき。{@code fullContent} はマーカー除去済みの全文。 */
    void onAssistantEnd(String fullContent);

    /** 次の質問に進んだとき。 */
    void onQuestionAdvance(String nextQuestionText, int questionIndex, int totalQuestions);

    /** 面接全体が終了したとき(AIが十分と判断した = {@code AI_JUDGED})。 */
    void onInterviewEnd();
}
