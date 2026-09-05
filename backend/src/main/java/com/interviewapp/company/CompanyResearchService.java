package com.interviewapp.company;

import com.interviewapp.company.CompanyDtos.GeneratedQuestions;
import com.interviewapp.company.CompanyDtos.ResearchDraft;
import com.interviewapp.company.CompanySourceDtos.FetchedSourcePreview;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 企業リサーチ(会社追加ウィザード)のロジック。
 *
 * <p>いずれのメソッドも DB には保存しない。会社レコードはウィザード最終ステップの
 * {@code CompanyService.create}(questions・sources 付き)で初めて作られる。ユーザーがウィザードの
 * ソース添付ステップで既にfetch済みのソースをそのまま渡してもらう前提で、ここでは再fetchしない。</p>
 */
@Service
public class CompanyResearchService {

    private final CompanyResearchLlm researchLlm;

    public CompanyResearchService(CompanyResearchLlm researchLlm) {
        this.researchLlm = researchLlm;
    }

    /**
     * 会社名＋登録済みソース(＋あれば現在の下書きとフィードバック)から企業概要の下書きを生成する。
     */
    public ResearchDraft research(
            String companyName, List<FetchedSourcePreview> sources, String currentOverview, String feedback) {
        String name = companyName.trim();
        String overview = researchLlm.summarizeOverview(name, sources, currentOverview, feedback);
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
