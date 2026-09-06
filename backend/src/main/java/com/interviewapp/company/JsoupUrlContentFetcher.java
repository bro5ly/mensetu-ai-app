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
 *
 * <p>任意のドメインをスクレイピングすることになる(特に企業リサーチの自動Web検索経由)ため、
 * 法的・倫理的リスクを下げる目的で {@link RobotsTxtChecker} によるrobots.txt尊重と
 * {@link HostFetchThrottle} による同一ホストへの連打防止を組み合わせている。</p>
 */
@Component
public class JsoupUrlContentFetcher implements UrlContentFetcher {

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; MensetuAiBot/1.0; +https://github.com/)";

    private final int timeoutMs;
    private final RobotsTxtChecker robotsTxtChecker;
    private final HostFetchThrottle hostFetchThrottle;

    public JsoupUrlContentFetcher(
            @Value("${app.sources.fetch-timeout-ms:10000}") int timeoutMs,
            RobotsTxtChecker robotsTxtChecker,
            HostFetchThrottle hostFetchThrottle) {
        this.timeoutMs = timeoutMs;
        this.robotsTxtChecker = robotsTxtChecker;
        this.hostFetchThrottle = hostFetchThrottle;
    }

    @Override
    public ExtractedContent fetch(String url) {
        if (!robotsTxtChecker.isAllowed(url)) {
            throw new IllegalStateException(
                    "このサイトはrobots.txtでクロールを許可していないため取得できません。");
        }
        hostFetchThrottle.awaitTurn(url, robotsTxtChecker.crawlDelayMs(url));
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
        String title = sanitize(doc.title());
        String content = sanitize(doc.body() != null ? doc.body().text() : doc.text());
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("ソースの取得に失敗しました。本文を抽出できませんでした。");
        }
        return new ExtractedContent(title == null || title.isBlank() ? null : title, content.trim());
    }

    /**
     * PostgresのTEXT/VARCHARはNUL文字(U+0000)を含む文字列を拒否する(UTF-8として妥当でも弾かれる)。
     * スクレイピングしたHTMLに稀に紛れ込むため、DBへ保存する前に取り除く。
     */
    private static String sanitize(String s) {
        if (s == null) {
            return null;
        }
        return s.replace(String.valueOf((char) 0), "");
    }
}
