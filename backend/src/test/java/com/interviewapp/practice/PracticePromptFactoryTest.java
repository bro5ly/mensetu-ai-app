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
                .contains("ユーザーが終了ボタンを押すまで、常にこの1問の回答づくりに付き合います");
    }

    @Test
    void 面接シミュレーションではなく回答づくりの相談役だと明示し面接官風の深掘りをしない指示になる() {
        String prompt = factory.systemPrompt("ABCコーポレーション", null, List.of(), "質問");

        assertThat(prompt)
                .contains("回答づくりの相談役")
                .contains("面接シミュレーションではなく")
                .contains("面接官のように質問を次々に投げかけて掘り下げることはしません")
                .contains("本番モードの役割");
    }

    @Test
    void 回答例でユーザーの経歴を捏造しない旨の注意が含まれる() {
        String prompt = factory.systemPrompt(
                "ABCコーポレーション", "従業員数1000人のIT企業", List.of(), "質問");

        assertThat(prompt)
                .contains("会社側の背景情報として参考にするだけにとどめ")
                .contains("一人称の実体験としてではなく、あくまで例として語ります");
    }

    @Test
    void STAR型とPREP型の回答フレームワークが含まれる() {
        String prompt = factory.systemPrompt("ABCコーポレーション", null, List.of(), "質問");

        assertThat(prompt)
                .contains("STAR型")
                .contains("状況(どんな場面だったか)")
                .contains("PREP型")
                .contains("結論(一番伝えたいこと)");
    }

    @Test
    void 会社概要とソースが無ければ企業情報タグの中身は未登録の表示になる() {
        String prompt = factory.systemPrompt("ABCコーポレーション", null, List.of(), "質問");

        assertThat(prompt)
                .contains("<企業情報>")
                .contains("</企業情報>")
                .contains("(登録されていません)");
    }

    @Test
    void 会社概要とソースがあれば企業情報タグの中に埋め込む() {
        String prompt = factory.systemPrompt(
                "ABCコーポレーション", "従業員数1000人のIT企業", List.of("新卒採用に力を入れている"), "質問");

        assertThat(prompt)
                .contains("<企業情報>")
                .contains("従業員数1000人のIT企業")
                .contains("新卒採用に力を入れている")
                .contains("</企業情報>");
    }

    @Test
    void 候補者情報と企業情報とあなたの役割はタグで明確に区切られる() {
        String prompt = factory.systemPrompt(
                "ABCコーポレーション", "会社概要", List.of(), "質問", "AtCoderでアルゴリズムを学習した。");

        assertThat(prompt)
                .contains("<候補者情報>")
                .contains("AtCoderでアルゴリズムを学習した。")
                .contains("</候補者情報>")
                .contains("<企業情報>")
                .contains("</企業情報>")
                .contains("<あなたの役割>")
                .contains("</あなたの役割>");

        int candidateIdx = prompt.indexOf("<候補者情報>");
        int companyIdx = prompt.indexOf("<企業情報>");
        int roleIdx = prompt.indexOf("<あなたの役割>");
        assertThat(candidateIdx).isLessThan(companyIdx);
        assertThat(companyIdx).isLessThan(roleIdx);
    }

    @Test
    void 候補者情報が無ければタグの中身は未登録の表示になる() {
        String prompt = factory.systemPrompt("ABCコーポレーション", null, List.of(), "質問", null);

        assertThat(prompt)
                .contains("<候補者情報>")
                .contains("</候補者情報>")
                .contains("(登録されていません)");
    }

    @Test
    void 候補者情報は参考にとどめ実際に話したことのように扱わない指示になる() {
        String prompt = factory.systemPrompt(
                "ABCコーポレーション", null, List.of(), "質問", "AtCoderでアルゴリズムを学習した。");

        assertThat(prompt)
                .contains("ユーザー本人が事前に登録した参考情報")
                .contains("あなた自身の体験であるかのように話したりはしません");
    }

    @Test
    void 回答内容が企業情報と整合しているか確認する指示が含まれる() {
        String prompt = factory.systemPrompt("ABCコーポレーション", null, List.of(), "質問");

        assertThat(prompt)
                .contains("回答内容の妥当性を確認する")
                .contains("事業内容・特徴と")
                .contains("整合しているかを確認します")
                .contains("具体的な理由や")
                .contains("根拠が伴っていない回答には、どんな具体例を足せば説得力が出るかを一緒に考えます");
    }

    @Test
    void 話し方の参考情報メモの扱い方の指示が含まれる() {
        String prompt = factory.systemPrompt("ABCコーポレーション", null, List.of(), "質問");

        assertThat(prompt)
                .contains("[話し方の参考情報]")
                .contains("システムが自動計測")
                .contains("種明かししたりせず");
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
