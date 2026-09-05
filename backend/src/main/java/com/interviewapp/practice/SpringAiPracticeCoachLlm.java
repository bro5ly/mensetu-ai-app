package com.interviewapp.practice;

import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

/** {@link PracticeCoachLlm} の Spring AI (Ollama) 実装。 */
@Component
public class SpringAiPracticeCoachLlm implements PracticeCoachLlm {

    /**
     * コーチの1返答は1〜2文(アドバイスでも3文以内)の音声向け短文で足りる。上限を高くすると
     * 小型モデルが同じ内容を延々と繰り返し、生成時間もTTSで読み上げる音声も長くなるため、
     * プロンプトの簡潔さの指示に合わせて出力トークンを絞る。
     */
    private static final OllamaOptions REPLY_OPTIONS = OllamaOptions.builder().numPredict(220).build();

    private final ChatClient chatClient;

    public SpringAiPracticeCoachLlm(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Flux<String> streamReply(String systemPrompt, List<Turn> history, String userMessage) {
        List<Message> historyMessages = history.stream()
                .map(SpringAiPracticeCoachLlm::toMessage)
                .toList();

        return chatClient.prompt()
                .system(systemPrompt)
                .messages(historyMessages)
                .user(userMessage)
                .options(REPLY_OPTIONS)
                .stream()
                .content()
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

    private static Message toMessage(Turn turn) {
        return turn.role() == Turn.Role.ASSISTANT
                ? new AssistantMessage(turn.content())
                : new UserMessage(turn.content());
    }
}
