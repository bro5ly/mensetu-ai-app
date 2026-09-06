package com.interviewapp.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SentenceSplitterTest {

    @Test
    void 句点で終わる完成した文だけを取り出し残りは保持する() {
        SentenceSplitter.Result result = SentenceSplitter.extract("なるほど。そのとき何を意識");

        assertThat(result.sentences()).containsExactly("なるほど。");
        assertThat(result.remainder()).isEqualTo("そのとき何を意識");
    }

    @Test
    void 複数文が一度に確定した場合は全て返す() {
        SentenceSplitter.Result result = SentenceSplitter.extract("いいですね！次に進みましょう。続き");

        assertThat(result.sentences()).containsExactly("いいですね！", "次に進みましょう。");
        assertThat(result.remainder()).isEqualTo("続き");
    }

    @Test
    void 疑問符で終わる場合も文として確定する() {
        SentenceSplitter.Result result = SentenceSplitter.extract("それはなぜですか?");

        assertThat(result.sentences()).containsExactly("それはなぜですか?");
        assertThat(result.remainder()).isEmpty();
    }

    @Test
    void 区切り文字が無ければ全体が残りになる() {
        SentenceSplitter.Result result = SentenceSplitter.extract("まだ途中の文章");

        assertThat(result.sentences()).isEmpty();
        assertThat(result.remainder()).isEqualTo("まだ途中の文章");
    }

    @Test
    void 空文字列は何も返さない() {
        SentenceSplitter.Result result = SentenceSplitter.extract("");

        assertThat(result.sentences()).isEmpty();
        assertThat(result.remainder()).isEmpty();
    }
}
