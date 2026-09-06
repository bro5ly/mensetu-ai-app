package com.interviewapp.chat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 同じ文面・話者の音声合成結果を再利用する、シンプルなLRUキャッシュ。
 *
 * <p>練習コーチの相づち・短い定型的な言い回しなど、同じ文が繰り返し生成されるケースで
 * audio_query/synthesisの往復を丸ごと省略できる。件数上限を超えたら最も使われていない
 * ものから捨てる(アクセス順LinkedHashMap)。</p>
 */
@Component
class TtsCache {

    private static final int MAX_ENTRIES = 200;

    private final Map<String, byte[]> entries = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    synchronized byte[] get(String text, int speakerId) {
        return entries.get(key(text, speakerId));
    }

    synchronized void put(String text, int speakerId, byte[] audio) {
        if (audio != null && audio.length > 0) {
            entries.put(key(text, speakerId), audio);
        }
    }

    private static String key(String text, int speakerId) {
        return speakerId + ":" + text;
    }
}
