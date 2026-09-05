package com.interviewapp.company;

import java.io.IOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@link UrlContentFetcher} の Jsoup 実装。URLを直接fetchしてHTMLをパースし、
 * タイトルと本文テキストを抽出する(APIキー不要、readability的な高度な本文抽出はせず
 * 素朴な body テキスト抽出にとどめる)。
 */
@Component
public class JsoupUrlContentFetcher implements UrlContentFetcher {

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; MensetuAiBot/1.0; +https://github.com/)";

    private final int timeoutMs;

    public JsoupUrlContentFetcher(@Value("${app.sources.fetch-timeout-ms:10000}") int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    @Override
    public ExtractedContent fetch(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(timeoutMs)
                    .get();
            return extract(doc);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "ソースの取得に失敗しました。URLを確認してください。", e);
        }
    }

    /** HTML→テキスト抽出部分。実ネットワークなしで単体テストできるよう分離。package-private: 単体テスト用。 */
    static ExtractedContent extract(Document doc) {
        String title = doc.title();
        String content = doc.body() != null ? doc.body().text() : doc.text();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("ソースの取得に失敗しました。本文を抽出できませんでした。");
        }
        return new ExtractedContent(title == null || title.isBlank() ? null : title, content.trim());
    }
}
