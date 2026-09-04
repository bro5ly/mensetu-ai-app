package com.interviewapp.practice;

import java.util.List;
import reactor.core.publisher.Flux;

/**
 * 練習コーチ役の LLM 呼び出しを抽象化するインターフェース。
 * Spring AI への依存をこの境界に閉じ込め、{@link PracticeTurnService} を単体テストしやすくする。
 */
public interface PracticeCoachLlm {

    /**
     * 会話履歴と最新のユーザー発話から、コーチの応答をストリーミングで生成する。
     *
     * @param systemPrompt システムプロンプト
     * @param history      これまでの会話（古い順）
     * @param userMessage  最新のユーザー発話
     * @return 応答テキストのチャンク列（{@code <<ADVICE>>} マーカーは未除去の生テキスト）
     */
    Flux<String> streamReply(String systemPrompt, List<Turn> history, String userMessage);

    /** 会話履歴の1発話。 */
    record Turn(Role role, String content) {

        public enum Role {
            USER,
            ASSISTANT
        }
    }
}
