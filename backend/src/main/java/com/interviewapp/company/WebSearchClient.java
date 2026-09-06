package com.interviewapp.company;

import java.util.List;

/**
 * 会社名からWebを検索するクライアント。実装はSearXNG(自己ホスト・APIキー不要)。
 * {@link CompanyResearchService} を単体テストしやすくするための境界({@code UrlContentFetcher}と同じ方針)。
 */
public interface WebSearchClient {

    /**
     * クエリでWeb検索し、上位 {@code maxResults} 件を返す。
     * 検索自体に失敗した場合は例外を投げず空リストを返す実装を想定する
     * (企業リサーチは検索サービスが落ちていてもユーザー提供ソースだけで続行できるべきため)。
     */
    List<SearchResult> search(String query, int maxResults);

    /** 検索結果1件(タイトル・URL・スニペット)。 */
    record SearchResult(String title, String url, String snippet) {
    }
}
