package com.interviewapp.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewapp.session.MessageType;
import org.junit.jupiter.api.Test;

class AssistantMarkerParserTest {

    private final AssistantMarkerParser parser = new AssistantMarkerParser();

    @Test
    void 冒頭にADVICEマーカーがある場合はADVICE種別になりマーカーが除去される() {
        AssistantMarkerParser.Result result = parser.parse("<<ADVICE>>まず結論から話しましょう。");

        assertThat(result.messageType()).isEqualTo(MessageType.ADVICE);
        assertThat(result.content()).isEqualTo("まず結論から話しましょう。");
    }

    @Test
    void マーカー前後の空白や改行を許容する() {
        AssistantMarkerParser.Result result = parser.parse("  <<ADVICE>>\n\n数字を入れると説得力が増します。");

        assertThat(result.messageType()).isEqualTo(MessageType.ADVICE);
        assertThat(result.content()).isEqualTo("数字を入れると説得力が増します。");
    }

    @Test
    void マーカーがない場合はNORMAL種別で本文はそのまま() {
        AssistantMarkerParser.Result result = parser.parse("そのとき何を意識しましたか？");

        assertThat(result.messageType()).isEqualTo(MessageType.NORMAL);
        assertThat(result.content()).isEqualTo("そのとき何を意識しましたか？");
    }

    @Test
    void マーカーが文中にある場合は検出しない() {
        AssistantMarkerParser.Result result = parser.parse("よい回答です <<ADVICE>> という書き方もあります");

        assertThat(result.messageType()).isEqualTo(MessageType.NORMAL);
        assertThat(result.content()).contains("<<ADVICE>>");
    }

    @Test
    void nullや空文字はNORMALかつ空文字を返す() {
        assertThat(parser.parse(null).messageType()).isEqualTo(MessageType.NORMAL);
        assertThat(parser.parse(null).content()).isEmpty();
        assertThat(parser.parse("   ").content()).isEmpty();
    }

    @Test
    void ストリーミング途中の短いプレフィックスでは判定を保留する() {
        assertThat(parser.canDecideFrom("<<AD")).isFalse();
        assertThat(parser.canDecideFrom("<<ADVICE>>")).isTrue();
        assertThat(parser.canDecideFrom("こんにちは")).isTrue();
    }
}
