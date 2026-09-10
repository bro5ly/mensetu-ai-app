package com.interviewapp.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewapp.session.MessageType;
import org.junit.jupiter.api.Test;

class WsEventCodecTest {

    private final WsEventCodec codec = new WsEventCodec(new ObjectMapper());

    @Test
    void 正常なJSONからtypeとtextを取り出す() {
        WsEventCodec.Inbound inbound = codec.parseInbound("{\"type\":\"user_text\",\"text\":\"こんにちは\"}");

        assertThat(inbound.type()).isEqualTo("user_text");
        assertThat(inbound.text()).isEqualTo("こんにちは");
    }

    @Test
    void 不正なJSONはtypeがnullになる() {
        assertThat(codec.parseInbound("not json").type()).isNull();
        assertThat(codec.parseInbound("{}").type()).isNull();
    }

    @Test
    void assistantMessageStartはmessageTypeを含む() {
        assertThat(codec.assistantMessageStart(MessageType.ADVICE))
                .contains("\"type\":\"assistant_message_start\"")
                .contains("\"messageType\":\"ADVICE\"");
    }

    @Test
    void transcriptとchunkとendとsessionEndedのJSON形状() {
        assertThat(codec.transcript("文字起こし")).contains("\"type\":\"transcript\"", "\"text\":\"文字起こし\"");
        assertThat(codec.assistantTextChunk("あ")).contains("\"type\":\"assistant_text_chunk\"");
        assertThat(codec.assistantMessageEnd()).contains("\"type\":\"assistant_message_end\"");
        assertThat(codec.sessionEnded("USER_ENDED")).contains("\"reason\":\"USER_ENDED\"");
    }

    @Test
    void partialTranscriptのJSON形状() {
        assertThat(codec.partialTranscript("学生時代に"))
                .contains("\"type\":\"partial_transcript\"", "\"text\":\"学生時代に\"");
    }

    @Test
    void mockQuestionAdvancedのJSON形状() {
        assertThat(codec.mockQuestionAdvanced("志望動機を教えてください", 2, 5))
                .contains("\"type\":\"mock_question_advanced\"")
                .contains("\"questionText\":\"志望動機を教えてください\"")
                .contains("\"questionIndex\":2")
                .contains("\"totalQuestions\":5");
    }

    @Test
    void reportReadyのJSON形状() {
        assertThat(codec.reportReady()).contains("\"type\":\"report_ready\"");
    }
}
