package com.interviewapp.company;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 同じホストへのfetchが短時間に連続しないよう最低間隔を空ける、ホスト単位のスロットル。
 *
 * <p>企業リサーチの自動Web検索(SearXNG)は複数のURLを次々fetchしうるため、バグや想定外の
 * 挙動で同じサイトを連打してしまうリスクを下げる目的で入れている。呼び出し自体を
 * synchronized にしているため、この処理系全体で見て一度に1つのfetchしか進めない
 * (このアプリの利用規模ではボトルネックにならない)。</p>
 */
@Component
class HostFetchThrottle {

    private final Map<String, Instant> lastAccessByHost = new ConcurrentHashMap<>();

    /** {@code minIntervalMs} だけ前回のこのホストへのアクセスから空くように、必要なら待つ。 */
    synchronized void awaitTurn(String url, long minIntervalMs) {
        String host = hostOf(url);
        if (host == null || minIntervalMs <= 0) {
            return;
        }
        Instant last = lastAccessByHost.get(host);
        if (last != null) {
            long elapsedMs = Duration.between(last, Instant.now()).toMillis();
            long remainingMs = minIntervalMs - elapsedMs;
            if (remainingMs > 0) {
                sleep(remainingMs);
            }
        }
        lastAccessByHost.put(host, Instant.now());
    }

    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
