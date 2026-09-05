package com.interviewapp.company;

import com.interviewapp.company.CompanyDtos.GeneratedQuestions;
import com.interviewapp.company.CompanyDtos.ResearchDraft;
import com.interviewapp.company.WebSearchClient.SearchResult;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 企業リサーチ(会社追加ウィザード)のロジック。
 *
 * <p>いずれのメソッドも DB には保存しない。会社レコードはウィザード最終ステップの
 * {@code CompanyService.create}(questions 付き)で初めて作られる。</p>
 */
@Service
public class CompanyResearchService {

    private final WebSearchClient webSearchClient;
    private final CompanyResearchLlm researchLlm;

    public CompanyResearchService(WebSearchClient webSearchClient, CompanyResearchLlm researchLlm) {
        this.webSearchClient = webSearchClient;
        this.researchLlm = researchLlm;
    }

    /**
     * 会社名(＋あれば現在の下書きとフィードバック)から企業概要の下書きを生成する。
     */
    public ResearchDraft research(String companyName, String currentOverview, String feedback) {
        String name = companyName.trim();
        String query = StringUtils.hasText(feedback) ? name + " " + feedback.trim() : name;
        List<SearchResult> results = webSearchClient.search(query);
        String overview = researchLlm.summarizeOverview(name, results, currentOverview, feedback);
        return new ResearchDraft(overview);
    }

    /**
     * 確認済みの企業概要から面接想定質問を 3 つ生成する。
     */
    public GeneratedQuestions generateQuestions(String companyName, String overview) {
        List<String> questions = researchLlm.generateQuestions(companyName.trim(), overview.trim());
        return new GeneratedQuestions(questions);
    }
}
