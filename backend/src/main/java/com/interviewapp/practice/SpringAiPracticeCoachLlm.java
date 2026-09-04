package com.interviewapp.practice;

import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/** {@link PracticeCoachLlm} の Spring AI (Ollama) 実装。 */
@Component
public class SpringAiPracticeCoachLlm implements PracticeCoachLlm {

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
                .stream()
                .content();
    }

    private static Message toMessage(Turn turn) {
        return turn.role() == Turn.Role.ASSISTANT
                ? new AssistantMessage(turn.content())
                : new UserMessage(turn.content());
    }
}
