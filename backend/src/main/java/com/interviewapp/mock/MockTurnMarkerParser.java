package com.interviewapp.mock;

import org.springframework.stereotype.Component;

/**
 * 本番模擬面接(一問一答形式)で、1つの質問への深掘りを終えるべきかどうかを示す
 * マーカー({@code <<QUESTION_DONE>>})を検出する純粋なパーサ。
 *
 * <p>このマーカーは応答の中に他の文章と一緒に付く想定ではなく、LLMには「深掘りを終える
 * 場合はマーカーだけを出力する(相槌や次の質問文は書かない)」と指示している。次に
 * 質問を進めるか面接を終えるかはコード側({@code MockTurnService})が質問フローの
 * 残りから確定的に決めるため、このパーサはマーカーの有無だけを判定すればよい。
 * 小型モデルがマーカーの前後に余計な文言を付けてしまった場合に備え、マーカーを
 * 検出したときは本文をそのまま使わず捨てる(次に話す内容はコード側で確定的に
 * 組み立てるため)。WebSocket ハンドラから切り離してあり、単体テストしやすいよう
 * 副作用を持たない。</p>
 */
@Component
public class MockTurnMarkerParser {

    static final String QUESTION_DONE_MARKER = "<<QUESTION_DONE>>";

    /**
     * 応答全文を受け取り、深掘りを終えるべきか(マーカー検出)と、続ける場合の
     * 深掘り質問の本文を返す。
     *
     * @param rawResponse LLM の生応答。null は空文字として扱う
     */
    public Result parse(String rawResponse) {
        if (rawResponse == null) {
            return new Result("", false);
        }
        String trimmed = rawResponse.strip();
        if (trimmed.contains(QUESTION_DONE_MARKER)) {
            return new Result("", true);
        }
        return new Result(trimmed, false);
    }

    /**
     * @param content 深掘りを続ける場合の質問文(マーカー検出時は常に空文字)
     * @param done    この質問への深掘りを終えるべきか({@code <<QUESTION_DONE>>}を検出した)
     */
    public record Result(String content, boolean done) {
    }
}
