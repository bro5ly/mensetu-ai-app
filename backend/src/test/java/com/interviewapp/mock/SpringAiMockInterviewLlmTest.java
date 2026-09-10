package com.interviewapp.mock;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewapp.mock.MockInterviewLlm.QuestionFeedback;
import com.interviewapp.mock.MockInterviewLlm.ReportResult;
import org.junit.jupiter.api.Test;

/** {@link SpringAiMockInterviewLlm#parseReport} のテスト。 */
class SpringAiMockInterviewLlmTest {

    @Test
    void レポートを正しくパースする() {
        String raw = """
                SCORE: 78
                TENDENCY: 結論から話す意識はあるが、具体性がやや浅い。
                Q1: 簡単に自己紹介をお願いします。
                A1: ゼミ活動とアルバイトの経験を話した。
                F1: 経歴の説明に終始しており、学びまで触れるとより良い。
                Q2: 当社を志望する理由を教えてください。
                A2: 社風に惹かれたと述べた。
                F2: 会社の具体的な事業内容との結びつきを示せるとより説得力が増す。
                """;

        ReportResult result = SpringAiMockInterviewLlm.parseReport(raw);

        assertThat(result).isNotNull();
        assertThat(result.score()).isEqualTo(78);
        assertThat(result.tendencyAnalysis()).contains("結論から話す意識はあるが");
        assertThat(result.feedbacks()).hasSize(2);
        assertThat(result.feedbacks().get(0)).isEqualTo(new QuestionFeedback(
                "簡単に自己紹介をお願いします。", "ゼミ活動とアルバイトの経験を話した。",
                "経歴の説明に終始しており、学びまで触れるとより良い。"));
        assertThat(result.feedbacks().get(1).questionText()).isEqualTo("当社を志望する理由を教えてください。");
    }

    @Test
    void スコアが無ければパース失敗としてnull() {
        String raw = "TENDENCY: 傾向分析です\nQ1: 質問\nA1: 回答\nF1: フィードバック";

        assertThat(SpringAiMockInterviewLlm.parseReport(raw)).isNull();
    }

    @Test
    void 傾向分析が無ければパース失敗としてnull() {
        String raw = "SCORE: 80\nQ1: 質問\nA1: 回答\nF1: フィードバック";

        assertThat(SpringAiMockInterviewLlm.parseReport(raw)).isNull();
    }

    @Test
    void フィードバックが欠けている質問は結果から除く() {
        String raw = """
                SCORE: 65
                TENDENCY: 全体的に無難だが印象に残りにくい。
                Q1: 質問1
                A1: 回答1
                F1: フィードバック1
                Q2: 質問2
                A2: 回答2
                """;

        ReportResult result = SpringAiMockInterviewLlm.parseReport(raw);

        assertThat(result.feedbacks()).hasSize(1);
        assertThat(result.feedbacks().get(0).questionText()).isEqualTo("質問1");
    }

    @Test
    void 空やnullはnull() {
        assertThat(SpringAiMockInterviewLlm.parseReport(null)).isNull();
        assertThat(SpringAiMockInterviewLlm.parseReport("   ")).isNull();
    }

    @Test
    void 質問が1件もパースできなくてもスコアと傾向分析があれば結果を返す() {
        String raw = "SCORE: 50\nTENDENCY: 会話がほとんど無かった。";

        ReportResult result = SpringAiMockInterviewLlm.parseReport(raw);

        assertThat(result).isNotNull();
        assertThat(result.score()).isEqualTo(50);
        assertThat(result.feedbacks()).isEmpty();
    }

    @Test
    void スコアの数値以外の後続文字は無視する() {
        String raw = "SCORE: 90点\nTENDENCY: 良好。";

        assertThat(SpringAiMockInterviewLlm.parseReport(raw).score()).isEqualTo(90);
    }
}
