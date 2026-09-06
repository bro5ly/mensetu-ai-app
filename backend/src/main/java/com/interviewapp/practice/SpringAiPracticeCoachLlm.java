package com.interviewapp.practice;

import com.interviewapp.llm.OllamaChatClient;
import com.interviewapp.llm.OllamaMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

/** {@link PracticeCoachLlm} の Ollama 実装({@link OllamaChatClient}経由、理由はそちらのJavadoc参照)。 */
@Component
public class SpringAiPracticeCoachLlm implements PracticeCoachLlm {

    /**
     * コーチの1返答は1〜2文(アドバイスでも3文以内)の音声向け短文で足りるよう、プロンプト側で
     * 簡潔さを指示している(実際の長さはプロンプトの指示に委ねる)。ただし上限(num_predict/num_ctx)
     * 自体を狙いの長さぎりぎりに絞ると、日本語のトークン効率次第で応答が不自然に途中で
     * 打ち切られる方が実害が大きいため、上限は余裕を持たせておく。
     * {@code num_ctx} も明示する: 企業概要が600〜900文字程度に長くなったため、それを含む
     * システムプロンプトがOllamaの既定コンテキスト長を超えて応答が打ち切られないようにする。
     */
    private static final Map<String, Object> REPLY_OPTIONS = Map.of("num_predict", 800, "num_ctx", 8192);

    private final OllamaChatClient ollama;
    private final String model;

    public SpringAiPracticeCoachLlm(OllamaChatClient ollama, @Value("${spring.ai.ollama.chat.options.model}") String model) {
        this.ollama = ollama;
        this.model = model;
    }

    @Override
    public Flux<String> streamReply(String systemPrompt, List<Turn> history, String userMessage) {
        List<OllamaMessage> messages = new ArrayList<>();
        messages.add(OllamaMessage.system(systemPrompt));
        history.forEach(turn -> messages.add(toMessage(turn)));
        messages.add(OllamaMessage.user(userMessage));

        return ollama.stream(model, messages, REPLY_OPTIONS)
                .onErrorMap(e -> !(e instanceof PracticeCoachException), SpringAiPracticeCoachLlm::translate);
    }

    /**
     * Ollama 由来の技術的な例外を、そのままフロントに出せる日本語メッセージに変換する。
     * package-private: 単体テスト用。
     */
    static PracticeCoachException translate(Throwable e) {
        if (e instanceof WebClientRequestException) {
            return new PracticeCoachException(
                    "AI サーバー(Ollama)に接続できませんでした。起動しているか確認してください。", e);
        }
        if (e instanceof WebClientResponseException response) {
            String body = response.getResponseBodyAsString();
            if (body != null && (body.contains("signal: killed") || body.contains("out of memory")
                    || body.contains("cannot allocate memory"))) {
                return new PracticeCoachException(
                        "AI モデルの読み込みに失敗しました(メモリ不足の可能性があります)。"
                                + "より軽量なモデルに切り替えるか、割り当てメモリを増やしてください。", e);
            }
            return new PracticeCoachException(
                    "AI サーバー(Ollama)がエラーを返しました(HTTP " + response.getStatusCode().value() + ")。", e);
        }
        return new PracticeCoachException("AI 応答の生成に失敗しました。", e);
    }

    private static OllamaMessage toMessage(Turn turn) {
        return turn.role() == Turn.Role.ASSISTANT
                ? OllamaMessage.assistant(turn.content())
                : OllamaMessage.user(turn.content());
    }
}
