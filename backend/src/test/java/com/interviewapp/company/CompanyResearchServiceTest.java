package com.interviewapp.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.interviewapp.company.CompanyDtos.GeneratedQuestions;
import com.interviewapp.company.CompanyDtos.ResearchDraft;
import com.interviewapp.company.CompanySourceDtos.FetchedSourcePreview;
import com.interviewapp.company.WebSearchClient.SearchResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyResearchServiceTest {

    @Mock
    private WebSearchClient webSearchClient;

    @Mock
    private CompanySourceService companySourceService;

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

        CompanyResearchService service = new CompanyResearchService(llm, webSearchClient, companySourceService);
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

        new CompanyResearchService(llm, webSearchClient, companySourceService)
                .research("ABC商事", List.of(), "既存の下書き", "評価制度について");

        assertThat(passedCurrent.get()).isEqualTo("既存の下書き");
        assertThat(passedFeedback.get()).isEqualTo("評価制度について");
    }

    @Test
    void 下書きがある再検索時は自動検索をしない() {
        CompanyResearchService service =
                new CompanyResearchService(stubLlm("概要", List.of()), webSearchClient, companySourceService);

        service.research("ABC商事", List.of(), "既存の下書き", null);

        verifyNoInteractions(webSearchClient);
        verifyNoInteractions(companySourceService);
    }

    @Test
    void ソースが空でもLLM要約を返す() {
        CompanyResearchService service =
                new CompanyResearchService(stubLlm("それでも概要", List.of()), webSearchClient, companySourceService);

        assertThat(service.research("無名株式会社", List.of(), null, null).overview()).isEqualTo("それでも概要");
    }

    @Test
    void 初回検索時はSearXNGで自動検索した結果をソースに加える() {
        when(webSearchClient.search("ABC商事 新卒 採用 面接 社風 業界動向", 5)).thenReturn(List.of(
                new SearchResult("採用ページ", "https://a.example.com", "スニペットA"),
                new SearchResult("口コミ", "https://b.example.com", "スニペットB")));
        when(companySourceService.previewFetch("https://a.example.com"))
                .thenReturn(new FetchedSourcePreview("https://a.example.com", "採用ページ", "本文A"));
        when(companySourceService.previewFetch("https://b.example.com"))
                .thenReturn(new FetchedSourcePreview("https://b.example.com", "口コミ", "本文B"));

        AtomicReference<List<FetchedSourcePreview>> passedSources = new AtomicReference<>();
        CompanyResearchLlm llm = new CompanyResearchLlm() {
            @Override
            public String summarizeOverview(String companyName, List<FetchedSourcePreview> s,
                    String currentOverview, String feedback) {
                passedSources.set(s);
                return "概要";
            }

            @Override
            public List<String> generateQuestions(String companyName, String overview) {
                return List.of();
            }
        };
        CompanyResearchService service = new CompanyResearchService(llm, webSearchClient, companySourceService);

        ResearchDraft draft = service.research("ABC商事", List.of(), null, null);

        assertThat(passedSources.get()).extracting(FetchedSourcePreview::url)
                .containsExactly("https://a.example.com", "https://b.example.com");
        assertThat(draft.sources()).extracting(FetchedSourcePreview::url)
                .containsExactly("https://a.example.com", "https://b.example.com");
    }

    @Test
    void 自動検索でfetchに失敗したURLはスキップして続行する() {
        when(webSearchClient.search(any(), anyInt())).thenReturn(List.of(
                new SearchResult("壊れたページ", "https://broken.example.com", "スニペット"),
                new SearchResult("採用ページ", "https://ok.example.com", "スニペット")));
        when(companySourceService.previewFetch("https://broken.example.com"))
                .thenThrow(new IllegalStateException("取得に失敗しました"));
        when(companySourceService.previewFetch("https://ok.example.com"))
                .thenReturn(new FetchedSourcePreview("https://ok.example.com", "採用ページ", "本文"));

        CompanyResearchService service =
                new CompanyResearchService(stubLlm("概要", List.of()), webSearchClient, companySourceService);

        ResearchDraft draft = service.research("ABC商事", List.of(), null, null);

        assertThat(draft.sources()).extracting(FetchedSourcePreview::url)
                .containsExactly("https://ok.example.com");
    }

    @Test
    void ユーザー提供ソースを自動検索より優先し重複URLは追加しない() {
        FetchedSourcePreview userSource = new FetchedSourcePreview("https://a.example.com", "ユーザー提供", "ユーザーの本文");
        when(webSearchClient.search(any(), anyInt())).thenReturn(List.of(
                new SearchResult("同じURL", "https://a.example.com", "スニペット")));
        when(companySourceService.previewFetch("https://a.example.com"))
                .thenReturn(new FetchedSourcePreview("https://a.example.com", "自動検索側", "自動検索の本文"));

        AtomicReference<List<FetchedSourcePreview>> passedSources = new AtomicReference<>();
        CompanyResearchLlm llm = new CompanyResearchLlm() {
            @Override
            public String summarizeOverview(String companyName, List<FetchedSourcePreview> s,
                    String currentOverview, String feedback) {
                passedSources.set(s);
                return "概要";
            }

            @Override
            public List<String> generateQuestions(String companyName, String overview) {
                return List.of();
            }
        };
        CompanyResearchService service = new CompanyResearchService(llm, webSearchClient, companySourceService);

        ResearchDraft draft = service.research("ABC商事", List.of(userSource), null, null);

        assertThat(passedSources.get()).hasSize(1);
        assertThat(passedSources.get().get(0).title()).isEqualTo("ユーザー提供");
        assertThat(draft.sources()).hasSize(1);
    }

    @Test
    void 質問生成はLLMの結果をそのまま返す() {
        CompanyResearchService service =
                new CompanyResearchService(stubLlm("概要", List.of("Q1", "Q2", "Q3")), webSearchClient, companySourceService);

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
