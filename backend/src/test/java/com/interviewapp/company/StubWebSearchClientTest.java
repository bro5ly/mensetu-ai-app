package com.interviewapp.company;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewapp.company.WebSearchClient.SearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class StubWebSearchClientTest {

    private final StubWebSearchClient client = new StubWebSearchClient();

    @Test
    void クエリを含む複数の検索結果を返す() {
        List<SearchResult> results = client.search("ABC商事");

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(r -> {
            assertThat(r.title()).contains("ABC商事");
            assertThat(r.snippet()).contains("ABC商事");
            assertThat(r.url()).startsWith("https://");
        });
    }

    @Test
    void 空クエリなら空リストを返す() {
        assertThat(client.search("")).isEmpty();
        assertThat(client.search("   ")).isEmpty();
    }
}
