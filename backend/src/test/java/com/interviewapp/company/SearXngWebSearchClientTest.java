package com.interviewapp.company;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewapp.company.WebSearchClient.SearchResult;
import java.io.IOException;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class SearXngWebSearchClientTest {

    private MockWebServer server;
    private SearXngWebSearchClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new SearXngWebSearchClient(RestClient.builder(), server.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void 検索結果をタイトルURLスニペットに変換する() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"results":[
                          {"title":"採用ページ","url":"https://example.com/careers","content":"若手に裁量がある"},
                          {"title":"口コミ","url":"https://example.com/reviews","content":"風通しが良い"}
                        ]}
                        """));

        List<SearchResult> results = client.search("ABC商事 新卒 採用", 5);

        assertThat(results).containsExactly(
                new SearchResult("採用ページ", "https://example.com/careers", "若手に裁量がある"),
                new SearchResult("口コミ", "https://example.com/reviews", "風通しが良い"));

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).contains("/search").contains("format=json");
    }

    @Test
    void maxResultsで件数を絞る() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"results":[
                          {"title":"1","url":"https://a.example.com","content":"a"},
                          {"title":"2","url":"https://b.example.com","content":"b"},
                          {"title":"3","url":"https://c.example.com","content":"c"}
                        ]}
                        """));

        List<SearchResult> results = client.search("query", 2);

        assertThat(results).hasSize(2);
    }

    @Test
    void 検索に失敗しても例外を投げず空リストを返す() {
        server.enqueue(new MockResponse().setResponseCode(500));

        List<SearchResult> results = client.search("query", 5);

        assertThat(results).isEmpty();
    }

    @Test
    void resultsが無い応答でも空リストを返す() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        assertThat(client.search("query", 5)).isEmpty();
    }
}
