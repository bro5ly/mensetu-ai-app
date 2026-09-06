package com.interviewapp.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * {@link VoicevoxTtsClient} をHTTPレベルで検証する。audio_query/synthesisの2段階呼び出しと、
 * synthesis前にspeedScaleを書き換える処理が正しく動くことを確認する。
 */
class VoicevoxTtsClientTest {

    private MockWebServer server;
    private VoicevoxTtsClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new VoicevoxTtsClient(RestClient.builder(), new ObjectMapper(), server.url("/").toString(), 1.2);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void createAudioQueryはtextとspeakerをクエリパラメータで送る() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"speedScale\":1.0,\"kana\":\"コンニチワ\"}"));

        String query = client.createAudioQuery("こんにちは", 3);

        assertThat(query).contains("kana");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).contains("/audio_query").contains("speaker=3");
    }

    @Test
    void createAudioQueryはテキストが空ならリクエストせずnullを返す() {
        assertThat(client.createAudioQuery("", 3)).isNull();
        assertThat(client.createAudioQuery(null, 3)).isNull();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void synthesizeFromQueryはspeedScaleを上書きしてから送る() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "audio/wav")
                .setBody("RIFF....WAVEfmt ")); // ダミーWAV本文(中身は検証しない)

        client.synthesizeFromQuery("{\"speedScale\":1.0,\"kana\":\"コンニチワ\"}", 3);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).contains("/synthesis").contains("speaker=3");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"speedScale\":1.2");
        assertThat(body).contains("\"kana\":\"コンニチワ\"");
    }

    @Test
    void synthesizeFromQueryはクエリが空なら空配列を返す() {
        assertThat(client.synthesizeFromQuery(null, 3)).isEmpty();
        assertThat(client.synthesizeFromQuery("", 3)).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void speedScale書き換えに失敗しても元のクエリのまま送信を続行する() throws InterruptedException {
        server.enqueue(new MockResponse().setBody(""));

        client.synthesizeFromQuery("これはJSONではない", 3);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getBody().readUtf8()).isEqualTo("これはJSONではない");
    }
}
