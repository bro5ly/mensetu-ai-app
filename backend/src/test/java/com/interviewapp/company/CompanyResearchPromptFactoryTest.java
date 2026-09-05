package com.interviewapp.company;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewapp.company.CompanySourceDtos.FetchedSourcePreview;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompanyResearchPromptFactoryTest {

    private final CompanyResearchPromptFactory factory = new CompanyResearchPromptFactory();

    @Test
    void 概要プロンプトに会社名とソース本文とフィードバックが含まれる() {
        String prompt = factory.overviewUserPrompt(
                "ABC商事",
                List.of(new FetchedSourcePreview("https://example.com", "採用ページ", "若手に裁量がある")),
                null,
                "評価制度について詳しく");

        assertThat(prompt).contains("ABC商事");
        assertThat(prompt).contains("若手に裁量がある");
        assertThat(prompt).contains("評価制度について詳しく");
    }

    @Test
    void ソースが空なら補う指示を入れる() {
        String prompt = factory.overviewUserPrompt("無名株式会社", List.of(), null, null);

        assertThat(prompt).contains("無名株式会社");
        assertThat(prompt).contains("ソースは登録されていません");
    }

    @Test
    void 再生成時は現在の下書きを土台にする旨を含める() {
        String prompt = factory.overviewUserPrompt(
                "ABC商事", List.of(), "既存の下書きテキスト", null);

        assertThat(prompt).contains("既存の下書きテキスト");
        assertThat(prompt).contains("土台");
    }

    @Test
    void 質問プロンプトに会社名と概要が含まれる() {
        String prompt = factory.questionsUserPrompt("ABC商事", "挑戦を後押しする文化");

        assertThat(prompt).contains("ABC商事");
        assertThat(prompt).contains("挑戦を後押しする文化");
    }
}
