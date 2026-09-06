package com.interviewapp.company;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewapp.company.RobotsTxtChecker.HostRules;
import org.junit.jupiter.api.Test;

class RobotsTxtCheckerTest {

    @Test
    void ワイルドカードのDisallowとCrawlDelayを読み取る() {
        String robots = """
                User-agent: *
                Disallow: /admin
                Disallow: /private
                Crawl-delay: 3
                """;

        HostRules rules = RobotsTxtChecker.parse(robots);

        assertThat(rules.disallow()).containsExactly("/admin", "/private");
        assertThat(rules.crawlDelayMs()).isEqualTo(3000);
    }

    @Test
    void 自分のUserAgent向けブロックがあればそちらを優先する() {
        String robots = """
                User-agent: *
                Disallow: /

                User-agent: MensetuAiBot
                Disallow: /private
                """;

        HostRules rules = RobotsTxtChecker.parse(robots);

        assertThat(rules.disallow()).containsExactly("/private");
    }

    @Test
    void 空のDisallowは許可を意味する() {
        String robots = """
                User-agent: *
                Disallow:
                """;

        HostRules rules = RobotsTxtChecker.parse(robots);

        assertThat(rules.disallow()).isEmpty();
    }

    @Test
    void CrawlDelayが極端に大きい場合は上限で丸める() {
        String robots = """
                User-agent: *
                Crawl-delay: 999
                """;

        HostRules rules = RobotsTxtChecker.parse(robots);

        assertThat(rules.crawlDelayMs()).isEqualTo(10_000);
    }

    @Test
    void robots_txtが空ならすべて許可でデフォルト間隔() {
        HostRules rules = RobotsTxtChecker.parse("");

        assertThat(rules.disallow()).isEmpty();
        assertThat(rules.crawlDelayMs()).isEqualTo(1000);
    }

    @Test
    void コメント行や行内コメントは無視する() {
        String robots = """
                # this is a comment
                User-agent: *
                Disallow: /admin # inline comment
                """;

        HostRules rules = RobotsTxtChecker.parse(robots);

        assertThat(rules.disallow()).containsExactly("/admin");
    }

    @Test
    void 一致するUserAgentが無ければ全て許可() {
        String robots = """
                User-agent: SomeOtherBot
                Disallow: /
                """;

        HostRules rules = RobotsTxtChecker.parse(robots);

        assertThat(rules.disallow()).isEmpty();
    }
}
