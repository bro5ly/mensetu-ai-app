package com.interviewapp.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewapp.practice.PracticeCoachLlm.Turn;
import com.interviewapp.session.ChatMessage;
import com.interviewapp.session.MessageRole;
import com.interviewapp.session.MessageType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PracticePromptFactoryTest {

    private final PracticePromptFactory factory = new PracticePromptFactory();

    @Test
    void システムプロンプトに会社名と質問とマーカールールが含まれる() {
        String prompt = factory.systemPrompt(
                "ABCコーポレーション", null, List.of(), "学生時代に力を入れたことを教えてください");

        assertThat(prompt)
                .contains("ABCコーポレーション")
                .contains("学生時代に力を入れたことを教えてください")
                .contains("<<ADVICE>>")
                .contains("あなたから面接を終了してはいけません");
    }

    @Test
    void 会社概要とソースが無ければ企業情報の見出しを含めない() {
        String prompt = factory.systemPrompt("ABCコーポレーション", null, List.of(), "質問");

        assertThat(prompt).doesNotContain("企業情報:");
    }

    @Test
    void 会社概要とソースがあれば企業情報として埋め込む() {
        String prompt = factory.systemPrompt(
                "ABCコーポレーション", "従業員数1000人のIT企業", List.of("新卒採用に力を入れている"), "質問");

        assertThat(prompt)
                .contains("企業情報:")
                .contains("従業員数1000人のIT企業")
                .contains("新卒採用に力を入れている");
    }

    @Test
    void 保存済みメッセージをLLM履歴に変換する() {
        UUID sessionId = UUID.randomUUID();
        List<ChatMessage> messages = List.of(
                new ChatMessage(sessionId, MessageRole.USER, "サークルでリーダーをしていました", MessageType.NORMAL, 0),
                new ChatMessage(sessionId, MessageRole.ASSISTANT, "苦労した点は？", MessageType.NORMAL, 1));

        List<Turn> history = factory.toHistory(messages);

        assertThat(history).extracting(Turn::role)
                .containsExactly(Turn.Role.USER, Turn.Role.ASSISTANT);
        assertThat(history).extracting(Turn::content)
                .containsExactly("サークルでリーダーをしていました", "苦労した点は？");
    }
}
