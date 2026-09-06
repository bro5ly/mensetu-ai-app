package com.interviewapp.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * {@link WhisperSttClient} をHTTPレベルで検証する。特に、無音区間でのハルシネーション対策である
 * {@code vad_filter=true} が実際にリクエストに含まれることを確認するのが主目的。
 */
class WhisperSttClientTest {

    private MockWebServer server;
    private WhisperSttClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new WhisperSttClient(RestClient.builder(), server.url("/").toString(), "base");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void vad_filterを付けてリクエストしテキストを返す() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"text\":\"学生時代に力を入れたことです\"}"));

        String transcript = client.transcribe(new byte[] {1, 2, 3}, "audio/webm");

        assertThat(transcript).isEqualTo("学生時代に力を入れたことです");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/audio/transcriptions");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("name=\"vad_filter\"");
        assertThat(body).contains("true");
        assertThat(body).contains("name=\"language\"");
    }

    @Test
    void 音声が空なら空文字を返しリクエストしない() {
        assertThat(client.transcribe(new byte[0], "audio/webm")).isEmpty();
        assertThat(client.transcribe(null, "audio/webm")).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void サーバーエラー時は例外を投げず空文字を返す() {
        server.enqueue(new MockResponse().setResponseCode(500));

        String transcript = client.transcribe(new byte[] {1, 2, 3}, "audio/webm");

        assertThat(transcript).isEmpty();
    }

    @Test
    void textフィールドが無ければ空文字を返す() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        assertThat(client.transcribe(new byte[] {1, 2, 3}, "audio/webm")).isEmpty();
    }
}
