package com.interviewapp.mock;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewapp.mock.MockInterviewLlm.QuestionTranscript;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockInterviewPromptFactoryTest {

    private final MockInterviewPromptFactory factory = new MockInterviewPromptFactory();

    @Test
    void 深掘りプロンプトはコーチではなく面接官として振る舞う指示を含む() {
        String prompt = factory.followUpSystemPrompt(
                "ABC商事", "会社概要", null, "学生時代に力を入れたことを教えてください", 1, 5);

        assertThat(prompt)
                .contains("面接官")
                .contains("コーチではなく")
                .contains("模範解答は示しません");
    }

    @Test
    void 深掘りプロンプトは今の質問に属する会話だけを扱う旨の指示を含む() {
        String prompt = factory.followUpSystemPrompt("ABC商事", "会社概要", null, "質問", 2, 5);

        assertThat(prompt)
                .contains("この質問についてのやり取りだけです")
                .contains("他の質問には触れません");
    }

    @Test
    void 深掘りプロンプトは技術的な話題の深掘りを最大1問にとどめる指示を含む() {
        String prompt = factory.followUpSystemPrompt("ABC商事", "会社概要", null, "質問", 1, 5);

        assertThat(prompt)
                .contains("ここは技術面接ではありません")
                .contains("同じ技術的な話題への深掘りは多くても1問にとどめます");
    }

    @Test
    void 深掘りプロンプトは会社情報を評価基準に使わない指示を含む() {
        String prompt = factory.followUpSystemPrompt("ABC商事", "会社概要", null, "質問", 1, 5);

        assertThat(prompt)
                .contains("会社情報はこの応答を組み立てる際の評価基準には使いません")
                .contains("評価は、面接後にまとめて行います");
    }

    @Test
    void 深掘りプロンプトは終了マーカーのみを出力する指示を含む() {
        String prompt = factory.followUpSystemPrompt("ABC商事", "会社概要", null, "質問", 2, 5);

        assertThat(prompt)
                .contains("<<QUESTION_DONE>>")
                .contains("他には何も書かず")
                .contains("相槌や挨拶、次の質問は書きません")
                .contains("そのあとどう進めるかはシステムが引き継ぎます");
    }

    @Test
    void 深掘りプロンプトは発言を引用して意図や経験を引き出す型が指示され感想や解説を述べない指示になる() {
        String prompt = factory.followUpSystemPrompt("ABC商事", "会社概要", null, "質問", 2, 5);

        assertThat(prompt)
                .contains("候補者が話した具体的な内容を")
                .contains("短く引用し")
                .contains("感想や評価")
                .contains("説明・解説")
                .contains("面接官は評価も解説もする立場では")
                .contains("聞き出す立場です");
    }

    @Test
    void 深掘りプロンプトには感想評価や学校用語の解説になってしまう悪い例と良い例が含まれる() {
        String prompt = factory.followUpSystemPrompt("ABC商事", "会社概要", null, "質問", 2, 5);

        assertThat(prompt)
                .contains("悪い例(感想・評価や、学校・用語の解説になっている)")
                .contains("大きな強みだと感じます")
                .contains("良い例(話した内容を引用し、意図や経験を引き出す)")
                .contains("そこではどのような工夫をされたのですか");
    }

    @Test
    void 深掘りプロンプトでは複数の話題をまとめて因果関係を推測しない指示になる() {
        String prompt = factory.followUpSystemPrompt("ABC商事", "会社概要", null, "質問", 2, 5);

        assertThat(prompt)
                .contains("引用は候補者が言った一つの具体的な事柄だけを短く取り上げます")
                .contains("勝手に推測して付け加えたりはしません");
    }

    @Test
    void 深掘りプロンプトでは既に話した理由を聞き返さず別の角度を尋ねる指示になる() {
        String prompt = factory.followUpSystemPrompt("ABC商事", "会社概要", null, "質問", 2, 5);

        assertThat(prompt)
                .contains("同じ「なぜ」「どうして」を")
                .contains("繰り返し尋ねません")
                .contains("まだ聞いていない")
                .contains("悪い例(理由を既に話しているのに同じ理由を聞き返し")
                .contains("良い例(既に話した理由には短く反応し");
    }

    @Test
    void 深掘りプロンプトでは候補者の発言を情報として客観的に分析するような言い方をしない指示になる() {
        String prompt = factory.followUpSystemPrompt("ABC商事", "会社概要", null, "質問", 2, 5);

        assertThat(prompt)
                .contains("「情報」や「内容」として")
                .contains("客観的に分析するような言い方")
                .contains("という具体的情報が");
    }

    @Test
    void 面接終了時の固定の締めくくり文言が定義されている() {
        assertThat(MockInterviewPromptFactory.INTERVIEW_CLOSING_MESSAGE)
                .contains("終了")
                .contains("お疲れ様");
    }

    @Test
    void 会社情報が無ければタグの中身は未登録の表示になる() {
        String prompt = factory.followUpSystemPrompt("ABC商事", null, null, "質問", 1, 5);

        assertThat(prompt)
                .contains("<会社情報>")
                .contains("</会社情報>")
                .contains("(登録されていません)");
    }

    @Test
    void 候補者情報と会社情報とあなたの役割はタグで明確に区切られる() {
        String prompt = factory.followUpSystemPrompt(
                "ABC商事", "会社概要", "AtCoderでアルゴリズムを学習した。", "質問", 1, 5);

        assertThat(prompt)
                .contains("<候補者情報>")
                .contains("AtCoderでアルゴリズムを学習した。")
                .contains("</候補者情報>")
                .contains("<会社情報>")
                .contains("会社概要")
                .contains("</会社情報>")
                .contains("<あなたの役割>")
                .contains("</あなたの役割>");

        // タグの出現順が「候補者情報 → 会社情報 → あなたの役割」になっている
        int candidateIdx = prompt.indexOf("<候補者情報>");
        int companyIdx = prompt.indexOf("<会社情報>");
        int roleIdx = prompt.indexOf("<あなたの役割>");
        assertThat(candidateIdx).isLessThan(companyIdx);
        assertThat(companyIdx).isLessThan(roleIdx);
    }

    @Test
    void 候補者情報が無ければタグの中身は未登録の表示になる() {
        String prompt = factory.followUpSystemPrompt("ABC商事", "会社概要", null, "質問", 1, 5);

        assertThat(prompt)
                .contains("<候補者情報>")
                .contains("</候補者情報>")
                .contains("(登録されていません)");
    }

    @Test
    void 候補者情報は参考として読むだけで自分の経験のように話さない指示になる() {
        String prompt = factory.followUpSystemPrompt(
                "ABC商事", "会社概要", "AtCoderでアルゴリズムを学習した。", "質問", 1, 5);

        assertThat(prompt)
                .contains("候補者本人が事前に登録した参考情報")
                .contains("読むだけ")
                .contains("あなた自身の経験・意見・趣味であるかのように話したり");
    }

    @Test
    void レポート生成のシステムプロンプトに出力形式の指示が含まれる() {
        String prompt = factory.reportSystemPrompt();

        assertThat(prompt)
                .contains("SCORE:")
                .contains("TENDENCY:")
                .contains("Q1:")
                .contains("A1:")
                .contains("F1:");
    }

    @Test
    void レポート生成のユーザープロンプトに書き起こしが含まれる() {
        String prompt = factory.reportUserPrompt("ABC商事", "会社概要", List.of(
                new QuestionTranscript("自己紹介をお願いします。", "候補者: ゼミ活動をしていました。")));

        assertThat(prompt)
                .contains("ABC商事")
                .contains("自己紹介をお願いします。")
                .contains("候補者: ゼミ活動をしていました。");
    }
}
