package com.interviewapp.company;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * {@link WebSearchClient} のSearXNG実装。自己ホストのSearXNG(APIキー不要、
 * {@code docker-compose.yml}の`searxng`サービス)の {@code GET /search?format=json} を叩く。
 *
 * <p>検索自体の失敗(SearXNG未起動・タイムアウト等)は企業リサーチ全体を止める理由にしないため、
 * 例外を投げず空リストを返す(呼び出し元はユーザー提供ソースだけで続行できる)。SearXNGは
 * ローカル自己ホストで通常は高速に応答するはずなので、応答が無い場合に備えて明示的に
 * タイムアウトを設定する(過去にOllama呼び出しでタイムアウト未設定によるハングを経験したため)。</p>
 */
@Component
public class SearXngWebSearchClient implements WebSearchClient {

    private static final Logger log = LoggerFactory.getLogger(SearXngWebSearchClient.class);
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 8000;

    private final RestClient restClient;

    public SearXngWebSearchClient(RestClient.Builder builder, @Value("${app.searxng.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);
        this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        try {
            SearxngResponse response = restClient.get()
                    .uri(uri -> uri.path("/search")
                            .queryParam("q", query)
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .body(SearxngResponse.class);
            if (response == null || response.results() == null) {
                return List.of();
            }
            return response.results().stream()
                    .limit(maxResults)
                    .map(r -> new SearchResult(r.title(), r.url(), r.content()))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("SearXNGでのWeb検索に失敗しました(結果0件で継続します): {}", e.getMessage());
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearxngResponse(@JsonProperty("results") List<SearxngResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearxngResult(
            @JsonProperty("title") String title,
            @JsonProperty("url") String url,
            @JsonProperty("content") String content) {
    }
}
