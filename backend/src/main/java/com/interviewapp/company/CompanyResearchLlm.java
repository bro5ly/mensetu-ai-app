package com.interviewapp.company;

import com.interviewapp.company.WebSearchClient.SearchResult;
import java.util.List;

/**
 * 企業リサーチで使う LLM 呼び出しを抽象化するインターフェース。
 * Spring AI への依存をこの境界に閉じ込め、{@link CompanyResearchService} を単体テストしやすくする
 * ({@code practice.PracticeCoachLlm} と同じ方針)。
 */
public interface CompanyResearchLlm {

    /**
     * 検索結果(＋あれば現在の下書きとユーザーのフィードバック)から、企業概要・社風・面接傾向を
     * 日本語でまとめ直す。
     *
     * @param companyName     会社名
     * @param results         Web 検索結果(0 件もありうる)
     * @param currentOverview 既存の下書き(再検索時のみ。無ければ null/空)
     * @param feedback        ユーザーが反映してほしい観点(任意。無ければ null/空)
     * @return まとめ直したテキスト
     */
    String summarizeOverview(
            String companyName, List<SearchResult> results, String currentOverview, String feedback);

    /**
     * 会社概要から面接想定質問をちょうど 3 つ生成する。
     *
     * @return 質問文のリスト(常に 1〜3 件)
     */
    List<String> generateQuestions(String companyName, String overview);
}
