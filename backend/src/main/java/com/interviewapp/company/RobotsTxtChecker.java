package com.interviewapp.company;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * robots.txt を簡易的に解釈し、URLのfetchが許可されているか・何秒間隔を空けるべきかを判定する。
 *
 * <p>企業リサーチの自動Web検索は任意のドメインをスクレイピングすることになるため、
 * 法的・倫理的リスクを下げる目的で最低限のrobots.txt尊重を行う。RFC 9309 の全機能
 * (ワイルドカード等)には対応せず、{@code User-agent} ブロックの {@code Disallow}(前方一致)と
 * {@code Crawl-delay} のみを解釈する簡易実装。取得・解析に失敗した場合はブロックしない
 * (過剰な防御をして通常のリサーチ機能を止めないため)。ドメインごとに結果をキャッシュし、
 * 同じホストに何度もrobots.txtを取りに行かない。</p>
 */
@Component
class RobotsTxtChecker {

    private static final Logger log = LoggerFactory.getLogger(RobotsTxtChecker.class);
    private static final String USER_AGENT = "MensetuAiBot";
    private static final long DEFAULT_CRAWL_DELAY_MS = 1000;
    /** サイトが極端に長いcrawl-delayを指定していても、これ以上は待たない(誤設定対策)。 */
    private static final long MAX_CRAWL_DELAY_MS = 10_000;
    private static final int ROBOTS_FETCH_TIMEOUT_MS = 5000;

    private final Map<String, HostRules> cache = new ConcurrentHashMap<>();

    boolean isAllowed(String url) {
        return rulesFor(url).map(rules -> {
            String path = normalizePath(url);
            return rules.disallow().stream().noneMatch(path::startsWith);
        }).orElse(true);
    }

    long crawlDelayMs(String url) {
        return rulesFor(url).map(HostRules::crawlDelayMs).orElse(DEFAULT_CRAWL_DELAY_MS);
    }

    private Optional<HostRules> rulesFor(String url) {
        String host = hostOf(url);
        if (host == null) {
            return Optional.empty();
        }
        return Optional.of(cache.computeIfAbsent(host, this::fetchRules));
    }

    private HostRules fetchRules(String host) {
        try {
            String body = Jsoup.connect("https://" + host + "/robots.txt")
                    .userAgent(USER_AGENT)
                    .timeout(ROBOTS_FETCH_TIMEOUT_MS)
                    .ignoreContentType(true)
                    .execute()
                    .body();
            return parse(body);
        } catch (RuntimeException | IOException e) {
            log.debug("robots.txtの取得に失敗しました(制限なしとして続行): {}", host);
            return new HostRules(List.of(), DEFAULT_CRAWL_DELAY_MS);
        }
    }

    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String normalizePath(String url) {
        try {
            String path = URI.create(url).getRawPath();
            return (path == null || path.isEmpty()) ? "/" : path;
        } catch (RuntimeException e) {
            return "/";
        }
    }

    /**
     * {@code User-agent} ブロックごとに {@code Disallow}/{@code Crawl-delay} を読み取る簡易パーサ。
     * 自分のUser-Agentに一致するブロックがあればそれを、無ければ {@code *} ブロックを使う。
     * package-private: 単体テスト用。
     */
    static HostRules parse(String robotsTxt) {
        if (robotsTxt == null || robotsTxt.isBlank()) {
            return new HostRules(List.of(), DEFAULT_CRAWL_DELAY_MS);
        }

        Map<String, List<String>> disallowByAgent = new LinkedHashMap<>();
        Map<String, Long> crawlDelayByAgent = new LinkedHashMap<>();
        List<String> currentAgents = new ArrayList<>();
        boolean groupOpen = false;

        for (String rawLine : robotsTxt.split("\\R")) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();

            switch (key) {
                case "user-agent" -> {
                    if (!groupOpen) {
                        currentAgents = new ArrayList<>();
                    }
                    String agent = value.toLowerCase(Locale.ROOT);
                    currentAgents.add(agent);
                    disallowByAgent.putIfAbsent(agent, new ArrayList<>());
                    groupOpen = true;
                }
                case "disallow" -> {
                    groupOpen = false;
                    if (!value.isEmpty()) {
                        for (String agent : currentAgents) {
                            disallowByAgent.computeIfAbsent(agent, a -> new ArrayList<>()).add(value);
                        }
                    }
                }
                case "crawl-delay" -> {
                    groupOpen = false;
                    try {
                        long seconds = Long.parseLong(value);
                        for (String agent : currentAgents) {
                            crawlDelayByAgent.put(agent, Math.min(seconds * 1000, MAX_CRAWL_DELAY_MS));
                        }
                    } catch (NumberFormatException ignored) {
                        // 不正な値は無視してデフォルトのcrawl-delayを使う
                    }
                }
                default -> groupOpen = false;
            }
        }

        String ourAgent = USER_AGENT.toLowerCase(Locale.ROOT);
        String matchedAgent = disallowByAgent.containsKey(ourAgent) ? ourAgent
                : disallowByAgent.containsKey("*") ? "*" : null;
        if (matchedAgent == null) {
            return new HostRules(List.of(), DEFAULT_CRAWL_DELAY_MS);
        }
        long crawlDelay = crawlDelayByAgent.getOrDefault(matchedAgent, DEFAULT_CRAWL_DELAY_MS);
        return new HostRules(disallowByAgent.get(matchedAgent), crawlDelay);
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash >= 0 ? line.substring(0, hash) : line;
    }

    /** 1ホスト分のrobots.txt解釈結果。 */
    record HostRules(List<String> disallow, long crawlDelayMs) {
    }
}
