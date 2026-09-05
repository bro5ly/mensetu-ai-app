package com.interviewapp.company;

import java.util.List;

/**
 * 企業リサーチ用の Web 検索を抽象化するインターフェース。
 *
 * <p>実プロバイダ(Tavily 等)への依存をこの境界に閉じ込め、{@link CompanyResearchService} を
 * 単体テストしやすくする。現フェーズの既定実装は {@link StubWebSearchClient}(固定データ)。</p>
 */
public interface WebSearchClient {

    /**
     * クエリ(通常は会社名、または「会社名 + 追加トピック」)で Web を検索し、上位の結果を返す。
     *
     * @return 検索結果(0 件もありうる)
     */
    List<SearchResult> search(String query);

    /** 検索結果の 1 件。 */
    record SearchResult(String title, String url, String snippet) {
    }
}
