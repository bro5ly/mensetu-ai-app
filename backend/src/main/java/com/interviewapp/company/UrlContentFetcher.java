package com.interviewapp.company;

/**
 * ソースURLの本文取得を抽象化するインターフェース。
 * 実プロバイダ(Tavily等)への依存を避け、サーバーが直接URLをfetchして本文抽出する
 * (APIキー不要)。{@link CompanySourceService} を単体テストしやすくするための境界。
 */
public interface UrlContentFetcher {

    /**
     * URLの内容を取得し、タイトルと本文テキストを抽出する。
     *
     * @throws IllegalStateException 取得・抽出に失敗した場合(ネットワークエラー、不正なURL等)
     */
    ExtractedContent fetch(String url);

    /** 抽出結果の1件。title は取得できないこともある(null 許容)。 */
    record ExtractedContent(String title, String content) {
    }
}
