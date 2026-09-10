package com.interviewapp.mock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MockTurnMarkerParserTest {

    private final MockTurnMarkerParser parser = new MockTurnMarkerParser();

    @Test
    void マーカーが無ければ本文をそのまま深掘りの質問として返す() {
        MockTurnMarkerParser.Result result = parser.parse("それは具体的にどういうことですか？");

        assertThat(result.content()).isEqualTo("それは具体的にどういうことですか？");
        assertThat(result.done()).isFalse();
    }

    @Test
    void QUESTION_DONEマーカーを検出すると本文は捨てられdoneになる() {
        MockTurnMarkerParser.Result result = parser.parse("<<QUESTION_DONE>>");

        assertThat(result.content()).isEmpty();
        assertThat(result.done()).isTrue();
    }

    @Test
    void マーカーの前後に余計な文言が付いていても検出してdoneとして扱う() {
        MockTurnMarkerParser.Result result =
                parser.parse("なるほど、よく分かりました。<<QUESTION_DONE>>");

        assertThat(result.content()).isEmpty();
        assertThat(result.done()).isTrue();
    }

    @Test
    void マーカー前後の空白は取り除く() {
        MockTurnMarkerParser.Result result = parser.parse("  それはなぜですか？   ");

        assertThat(result.content()).isEqualTo("それはなぜですか？");
        assertThat(result.done()).isFalse();
    }

    @Test
    void nullは空文字として扱う() {
        MockTurnMarkerParser.Result result = parser.parse(null);

        assertThat(result.content()).isEmpty();
        assertThat(result.done()).isFalse();
    }
}
