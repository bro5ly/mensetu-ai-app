package com.interviewapp.company;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewapp.company.CompanyDtos.GeneratedQuestions;
import com.interviewapp.company.CompanyDtos.ResearchDraft;
import com.interviewapp.company.CompanySourceDtos.FetchedSourcePreview;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CompanyResearchServiceTest {

    @Test
    void 登録済みソースをLLMに渡して概要下書きを返す() {
        AtomicReference<List<FetchedSourcePreview>> passedSources = new AtomicReference<>();
        List<FetchedSourcePreview> sources =
                List.of(new FetchedSourcePreview("https://example.com", "採用ページ", "若手に裁量がある"));
        CompanyResearchLlm llm = new CompanyResearchLlm() {
            @Override
            public String summarizeOverview(String companyName, List<FetchedSourcePreview> s,
                    String currentOverview, String feedback) {
                passedSources.set(s);
                return "概要: " + companyName;
            }

            @Override
            public List<String> generateQuestions(String companyName, String overview) {
                return List.of();
            }
        };

        CompanyResearchService service = new CompanyResearchService(llm);
        ResearchDraft draft = service.research("ABC商事", sources, null, null);

        assertThat(draft.overview()).isEqualTo("概要: ABC商事");
        assertThat(passedSources.get()).hasSize(1);
    }

    @Test
    void フィードバックと現在の下書きがそのままLLMに渡される() {
        AtomicReference<String> passedFeedback = new AtomicReference<>();
        AtomicReference<String> passedCurrent = new AtomicReference<>();
        CompanyResearchLlm llm = new CompanyResearchLlm() {
            @Override
            public String summarizeOverview(String companyName, List<FetchedSourcePreview> s,
                    String currentOverview, String feedback) {
                passedCurrent.set(currentOverview);
                passedFeedback.set(feedback);
                return "概要";
            }

            @Override
            public List<String> generateQuestions(String companyName, String overview) {
                return List.of();
            }
        };

        new CompanyResearchService(llm).research("ABC商事", List.of(), "既存の下書き", "評価制度について");

        assertThat(passedCurrent.get()).isEqualTo("既存の下書き");
        assertThat(passedFeedback.get()).isEqualTo("評価制度について");
    }

    @Test
    void ソースが空でもLLM要約を返す() {
        CompanyResearchService service = new CompanyResearchService(stubLlm("それでも概要", List.of()));

        assertThat(service.research("無名株式会社", List.of(), null, null).overview()).isEqualTo("それでも概要");
    }

    @Test
    void 質問生成はLLMの結果をそのまま返す() {
        CompanyResearchService service = new CompanyResearchService(stubLlm("概要", List.of("Q1", "Q2", "Q3")));

        GeneratedQuestions generated = service.generateQuestions("ABC商事", "概要テキスト");

        assertThat(generated.questions()).containsExactly("Q1", "Q2", "Q3");
    }

    private static CompanyResearchLlm stubLlm(String overview, List<String> questions) {
        return new CompanyResearchLlm() {
            @Override
            public String summarizeOverview(String companyName, List<FetchedSourcePreview> sources,
                    String currentOverview, String feedback) {
                return overview;
            }

            @Override
            public List<String> generateQuestions(String companyName, String overview) {
                return questions;
            }
        };
    }
}
