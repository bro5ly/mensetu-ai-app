package com.interviewapp.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TtsCacheTest {

    @Test
    void 同じ文と話者なら保存した音声を返す() {
        TtsCache cache = new TtsCache();
        byte[] audio = {1, 2, 3};

        cache.put("こんにちは", 3, audio);

        assertThat(cache.get("こんにちは", 3)).isEqualTo(audio);
    }

    @Test
    void 話者が違えば別扱いになる() {
        TtsCache cache = new TtsCache();
        cache.put("こんにちは", 3, new byte[] {1});

        assertThat(cache.get("こんにちは", 8)).isNull();
    }

    @Test
    void 未登録の文はnull() {
        TtsCache cache = new TtsCache();

        assertThat(cache.get("まだ無い文", 3)).isNull();
    }

    @Test
    void 空やnullの音声は保存しない() {
        TtsCache cache = new TtsCache();

        cache.put("空の音声", 3, new byte[0]);
        cache.put("nullの音声", 3, null);

        assertThat(cache.get("空の音声", 3)).isNull();
        assertThat(cache.get("nullの音声", 3)).isNull();
    }
}
