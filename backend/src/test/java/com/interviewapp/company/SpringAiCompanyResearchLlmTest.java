package com.interviewapp.company;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link SpringAiCompanyResearchLlm#parseQuestionLines} の行パースのテスト。 */
class SpringAiCompanyResearchLlmTest {

    @Test
    void 素の3行をそのまま返す() {
        String raw = "志望動機を教えてください\n強みと弱みは何ですか\n最後に質問はありますか";

        assertThat(SpringAiCompanyResearchLlm.parseQuestionLines(raw))
                .containsExactly("志望動機を教えてください", "強みと弱みは何ですか", "最後に質問はありますか");
    }

    @Test
    void 番号や記号や空行を落とす() {
        String raw = "1. 志望動機を教えてください\n\n- 強みは何ですか\n・学生時代に力を入れたことは\n";

        assertThat(SpringAiCompanyResearchLlm.parseQuestionLines(raw))
                .containsExactly("志望動機を教えてください", "強みは何ですか", "学生時代に力を入れたことは");
    }

    @Test
    void 行が多くても先頭3件に丸める() {
        String raw = "質問その1です\n質問その2です\n質問その3です\n質問その4です";

        assertThat(SpringAiCompanyResearchLlm.parseQuestionLines(raw)).hasSize(3);
    }

    @Test
    void 重複行は除く() {
        String raw = "同じ質問です\n同じ質問です\n別の質問です";

        assertThat(SpringAiCompanyResearchLlm.parseQuestionLines(raw))
                .containsExactly("同じ質問です", "別の質問です");
    }

    @Test
    void 空やnullは空リスト() {
        assertThat(SpringAiCompanyResearchLlm.parseQuestionLines(null)).isEmpty();
        assertThat(SpringAiCompanyResearchLlm.parseQuestionLines("   \n  \n")).isEmpty();
    }

    @Test
    void 短すぎる断片は捨てる() {
        String raw = "はい\nこれはちゃんとした長さの質問です\nうん";

        assertThat(SpringAiCompanyResearchLlm.parseQuestionLines(raw))
                .containsExactly("これはちゃんとした長さの質問です");
    }
}
