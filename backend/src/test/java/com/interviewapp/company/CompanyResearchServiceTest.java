package com.interviewapp.company;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewapp.company.CompanyDtos.GeneratedQuestions;
import com.interviewapp.company.CompanyDtos.ResearchDraft;
import com.interviewapp.company.WebSearchClient.SearchResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CompanyResearchServiceTest {

    @Test
    void 検索結果をLLMに渡して概要下書きを返す() {
        AtomicReference<List<SearchResult>> passedResults = new AtomicReference<>();
        WebSearchClient search = query -> List.of(
                new SearchResult("t", "https://example.com", "snippet about " + query));
        CompanyResearchLlm llm = new CompanyResearchLlm() {
            @Override
            public String summarizeOverview(String companyName, List<SearchResult> results,
                    String currentOverview, String feedback) {
                passedResults.set(results);
                return "概要: " + companyName;
            }

            @Override
            public List<String> generateQuestions(String companyName, String overview) {
                return List.of();
            }
        };

        CompanyResearchService service = new CompanyResearchService(search, llm);
        ResearchDraft draft = service.research("ABC商事", null, null);

        assertThat(draft.overview()).isEqualTo("概要: ABC商事");
        assertThat(passedResults.get()).hasSize(1);
    }

    @Test
    void フィードバックはクエリに連結される() {
        AtomicReference<String> query = new AtomicReference<>();
        WebSearchClient search = q -> {
            query.set(q);
            return List.of();
        };
        CompanyResearchLlm llm = stubLlm("概要", List.of());

        new CompanyResearchService(search, llm).research("ABC商事", "既存の下書き", "評価制度について");

        assertThat(query.get()).isEqualTo("ABC商事 評価制度について");
    }

    @Test
    void 検索結果が空でもLLM要約を返す() {
        CompanyResearchService service =
                new CompanyResearchService(q -> List.of(), stubLlm("それでも概要", List.of()));

        assertThat(service.research("無名株式会社", null, null).overview()).isEqualTo("それでも概要");
    }

    @Test
    void 質問生成はLLMの結果をそのまま返す() {
        CompanyResearchService service = new CompanyResearchService(
                q -> List.of(), stubLlm("概要", List.of("Q1", "Q2", "Q3")));

        GeneratedQuestions generated = service.generateQuestions("ABC商事", "概要テキスト");

        assertThat(generated.questions()).containsExactly("Q1", "Q2", "Q3");
    }

    private static CompanyResearchLlm stubLlm(String overview, List<String> questions) {
        return new CompanyResearchLlm() {
            @Override
            public String summarizeOverview(String companyName, List<SearchResult> results,
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
