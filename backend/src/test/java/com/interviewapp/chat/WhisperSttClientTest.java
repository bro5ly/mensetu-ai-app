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
 * {@code vad_filter=true} が実際にリクエストに含まれること、および話速分析
 * ({@link SpeechMetricsAnalyzer}参照)に使う{@code verbose_json}のduration/segmentsが
 * 正しくパースされることを確認するのが主目的。
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
    void vad_filterとverbose_jsonを付けてリクエストしテキストを返す() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"text\":\"学生時代に力を入れたことです\"}"));

        TranscriptionResult result = client.transcribe(new byte[] {1, 2, 3}, "audio/webm");

        assertThat(result.text()).isEqualTo("学生時代に力を入れたことです");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/audio/transcriptions");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("name=\"vad_filter\"");
        assertThat(body).contains("true");
        assertThat(body).contains("name=\"language\"");
        assertThat(body).contains("name=\"response_format\"");
        assertThat(body).contains("verbose_json");
    }

    @Test
    void durationとsegmentsをパースする() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "text": "学生時代に力を入れたことです",
                          "duration": 3.5,
                          "segments": [
                            {"start": 0.2, "end": 1.5},
                            {"start": 2.0, "end": 3.4}
                          ]
                        }
                        """));

        TranscriptionResult result = client.transcribe(new byte[] {1, 2, 3}, "audio/webm");

        assertThat(result.durationSeconds()).isEqualTo(3.5);
        assertThat(result.segments()).containsExactly(
                new TranscriptionResult.Segment(0.2, 1.5),
                new TranscriptionResult.Segment(2.0, 3.4));
        assertThat(result.longestGapSeconds()).isEqualTo(0.5, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void durationが無ければnullのまま返す() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"text\":\"テスト\"}"));

        TranscriptionResult result = client.transcribe(new byte[] {1, 2, 3}, "audio/webm");

        assertThat(result.durationSeconds()).isNull();
        assertThat(result.segments()).isEmpty();
    }

    @Test
    void 音声が空なら空の結果を返しリクエストしない() {
        assertThat(client.transcribe(new byte[0], "audio/webm").text()).isEmpty();
        assertThat(client.transcribe(null, "audio/webm").text()).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void サーバーエラー時は例外を投げず空の結果を返す() {
        server.enqueue(new MockResponse().setResponseCode(500));

        TranscriptionResult result = client.transcribe(new byte[] {1, 2, 3}, "audio/webm");

        assertThat(result.text()).isEmpty();
        assertThat(result.durationSeconds()).isNull();
    }

    @Test
    void textフィールドが無ければ空文字を返す() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        assertThat(client.transcribe(new byte[] {1, 2, 3}, "audio/webm").text()).isEmpty();
    }
}
