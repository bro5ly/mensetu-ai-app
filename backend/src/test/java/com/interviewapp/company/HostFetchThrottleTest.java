package com.interviewapp.company;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HostFetchThrottleTest {

    @Test
    void 同じホストへの連続アクセスは最低間隔だけ待たされる() {
        HostFetchThrottle throttle = new HostFetchThrottle();

        long start = System.currentTimeMillis();
        throttle.awaitTurn("https://example.com/a", 200);
        throttle.awaitTurn("https://example.com/b", 200);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isGreaterThanOrEqualTo(180);
    }

    @Test
    void 異なるホストへのアクセスは待たされない() {
        HostFetchThrottle throttle = new HostFetchThrottle();

        long start = System.currentTimeMillis();
        throttle.awaitTurn("https://a.example.com", 500);
        throttle.awaitTurn("https://b.example.com", 500);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isLessThan(400);
    }

    @Test
    void minIntervalMsが0以下なら待たない() {
        HostFetchThrottle throttle = new HostFetchThrottle();

        long start = System.currentTimeMillis();
        throttle.awaitTurn("https://example.com", 0);
        throttle.awaitTurn("https://example.com", 0);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isLessThan(100);
    }

    @Test
    void URLが不正でも例外を投げない() {
        HostFetchThrottle throttle = new HostFetchThrottle();

        throttle.awaitTurn("not a url", 500);
    }
}
