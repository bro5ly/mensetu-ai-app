package com.interviewapp.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * {@link OllamaChatClient} をHTTPレベルで検証する。Ollamaの{@code /api/chat}が返す
 * NDJSON(改行区切りJSON)ストリームを、Spring WebFluxのデコーダが正しく断片ごとに
 * 分割できることを確認するのが主目的(思考モード無効化の前提となる実装)。
 */
class OllamaChatClientTest {

    private MockWebServer server;
    private OllamaChatClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new OllamaChatClient(WebClient.builder(), server.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void 非ストリーミング呼び出しでthinkをfalseで送り本文を取り出す() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"model":"qwen3.5:4b","message":{"role":"assistant","content":"こんにちは"},"done":true,"done_reason":"stop"}
                        """));

        String result = client.call("qwen3.5:4b",
                List.of(OllamaMessage.system("sys"), OllamaMessage.user("user")),
                Map.of("num_predict", 100));

        assertThat(result).isEqualTo("こんにちは");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/chat");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"think\":false");
        assertThat(body).contains("\"stream\":false");
        assertThat(body).contains("\"num_predict\":100");
    }

    @Test
    void 本文が空応答なら空文字を返す() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"model":"qwen3.5:4b","message":{"role":"assistant","content":""},"done":true,"done_reason":"length"}
                        """));

        String result = client.call("qwen3.5:4b", List.of(OllamaMessage.user("user")), Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    void ストリーミング呼び出しはNDJSONを断片として順に流し空文字チャンクは除外する() throws InterruptedException {
        String ndjson = String.join("\n",
                "{\"model\":\"qwen3.5:4b\",\"message\":{\"role\":\"assistant\",\"content\":\"こ\"},\"done\":false}",
                "{\"model\":\"qwen3.5:4b\",\"message\":{\"role\":\"assistant\",\"content\":\"んにちは\"},\"done\":false}",
                "{\"model\":\"qwen3.5:4b\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,\"done_reason\":\"stop\"}")
                + "\n";
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/x-ndjson")
                .setBody(ndjson));

        Flux<String> flux = client.stream("qwen3.5:4b", List.of(OllamaMessage.user("user")), Map.of("num_predict", 420));

        StepVerifier.create(flux)
                .expectNext("こ")
                .expectNext("んにちは")
                .verifyComplete();

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"think\":false");
        assertThat(body).contains("\"stream\":true");
    }
}
