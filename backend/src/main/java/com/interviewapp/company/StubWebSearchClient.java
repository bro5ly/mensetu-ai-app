package com.interviewapp.company;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link WebSearchClient} のスタブ実装。実際には Web を検索せず、クエリを差し込んだ
 * 決定論的な検索結果を返す。
 *
 * <p>目的は企業リサーチのパイプライン(検索 → LLM 要約 → 質問生成)と UX を先に完成させること。
 * 実プロバイダを導入するときは、この実装を差し替える(または {@code @ConditionalOnProperty} で切り替える)。</p>
 */
@Component
public class StubWebSearchClient implements WebSearchClient {

    private static final Logger log = LoggerFactory.getLogger(StubWebSearchClient.class);

    @Override
    public List<SearchResult> search(String query) {
        String q = query == null ? "" : query.trim();
        log.warn("スタブの Web 検索を使用中です(実際には検索しません)。query='{}'", q);
        if (q.isEmpty()) {
            return List.of();
        }
        return List.of(
                new SearchResult(
                        q + " 採用・求める人物像",
                        "https://example.com/" + slug(q) + "/recruit",
                        q + " は挑戦を後押しする文化を掲げ、若手にも早い段階で裁量のある仕事を任せる傾向がある。"
                                + "採用では主体性と、チームで成果を出した具体的な経験を重視するとされる。"),
                new SearchResult(
                        q + " 面接体験談",
                        "https://example.com/" + slug(q) + "/interview-report",
                        q + " の面接は和やかな雰囲気で、志望動機の一貫性と、経験から何を学んだかを掘り下げられることが多い。"
                                + "逆質問の内容もよく見られる。"),
                new SearchResult(
                        q + " 事業・カルチャー",
                        "https://example.com/" + slug(q) + "/about",
                        q + " は顧客との長期的な関係づくりと、部門を越えた連携を大切にしている。"
                                + "社会的な課題解決を事業の軸に据えているという発信が目立つ。"));
    }

    private static String slug(String s) {
        String slug = s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "company" : slug;
    }
}
