package com.interviewapp.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/** {@link SpringAiPracticeCoachLlm#translate} の分岐を検証する。 */
class SpringAiPracticeCoachLlmTest {

    @Test
    void 接続失敗はOllama未起動のメッセージになる() {
        WebClientRequestException e = new WebClientRequestException(
                new java.net.ConnectException("Connection refused"),
                HttpMethod.POST, URI.create("http://ollama:11434/api/chat"), new HttpHeaders());

        PracticeCoachException translated = SpringAiPracticeCoachLlm.translate(e);

        assertThat(translated.getMessage()).contains("接続できませんでした");
        assertThat(translated.getCause()).isSameAs(e);
    }

    @Test
    void モデル読み込み失敗のレスポンスはメモリ不足のヒントを出す() {
        WebClientResponseException e = WebClientResponseException.create(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", new HttpHeaders(),
                "{\"error\":\"llama-server process has terminated: signal: killed\"}".getBytes(), null);

        PracticeCoachException translated = SpringAiPracticeCoachLlm.translate(e);

        assertThat(translated.getMessage()).contains("メモリ不足");
    }

    @Test
    void その他の5xxはHTTPステータスを含む汎用メッセージになる() {
        WebClientResponseException e = WebClientResponseException.create(
                HttpStatus.BAD_GATEWAY.value(), "Bad Gateway", new HttpHeaders(), new byte[0], null);

        PracticeCoachException translated = SpringAiPracticeCoachLlm.translate(e);

        assertThat(translated.getMessage()).contains("502");
    }

    @Test
    void 既にPracticeCoachExceptionならそのまま扱えるよう別種の例外は汎用メッセージ() {
        PracticeCoachException translated = SpringAiPracticeCoachLlm.translate(new IllegalStateException("boom"));

        assertThat(translated.getMessage()).isEqualTo("AI 応答の生成に失敗しました。");
    }
}
