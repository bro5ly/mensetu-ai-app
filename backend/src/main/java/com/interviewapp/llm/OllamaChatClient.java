package com.interviewapp.llm;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * Ollama の {@code /api/chat} を直接叩く薄いクライアント。
 *
 * <p>Spring AI の {@code ChatClient} を使わない理由: このプロジェクトが使っている
 * Spring AI(1.0.0-M5)は Ollama の {@code think} リクエストフィールドを露出しておらず、
 * Qwen3系のようなハイブリッド思考モデルで「thinking だけで num_predict を使い切り、
 * 本文が空応答になる」問題を止められない。{@code think:false} は Ollama 自体には効くことを
 * 確認済みなので、ここでは生の JSON を組み立てて明示的に送る。</p>
 */
@Component
public class OllamaChatClient {

    private final WebClient webClient;

    public OllamaChatClient(WebClient.Builder builder, @Value("${spring.ai.ollama.base-url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    /** 非ストリーミング呼び出し。応答本文(無ければ空文字)を返す。 */
    public String call(String model, List<OllamaMessage> messages, Map<String, Object> options) {
        OllamaChatResponse response = webClient.post()
                .uri("/api/chat")
                .bodyValue(new OllamaChatRequest(model, messages, false, false, options))
                .retrieve()
                .bodyToMono(OllamaChatResponse.class)
                .block();
        return content(response);
    }

    /** ストリーミング呼び出し。本文の断片を順に流す(空文字チャンクは除外)。 */
    public Flux<String> stream(String model, List<OllamaMessage> messages, Map<String, Object> options) {
        return webClient.post()
                .uri("/api/chat")
                .bodyValue(new OllamaChatRequest(model, messages, true, false, options))
                .retrieve()
                .bodyToFlux(OllamaChatResponse.class)
                .map(OllamaChatClient::content)
                .filter(text -> !text.isEmpty());
    }

    private static String content(OllamaChatResponse response) {
        if (response == null || response.message() == null || response.message().content() == null) {
            return "";
        }
        return response.message().content();
    }
}
