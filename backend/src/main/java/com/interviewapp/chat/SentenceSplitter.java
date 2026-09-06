package com.interviewapp.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * ストリーミング中のテキストから、TTS合成に回せる「区切りの確定した文」を句読点で逐次取り出す純粋関数。
 * まだ区切りが来ていない残りは呼び出し元が次回分と連結して再度渡す想定。
 *
 * <p>これにより、LLM が全文を生成し終える前に先頭の文から順に TTS 合成を開始でき、
 * テキスト生成と音声合成・再生を並行させてレイテンシを縮められる。</p>
 */
final class SentenceSplitter {

    private static final String BOUNDARY_CHARS = "。！？!?";

    private SentenceSplitter() {
    }

    static Result extract(String buffer) {
        if (buffer == null || buffer.isEmpty()) {
            return new Result(List.of(), "");
        }
        List<String> sentences = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < buffer.length(); i++) {
            if (BOUNDARY_CHARS.indexOf(buffer.charAt(i)) >= 0) {
                sentences.add(buffer.substring(start, i + 1));
                start = i + 1;
            }
        }
        return new Result(sentences, buffer.substring(start));
    }

    record Result(List<String> sentences, String remainder) {
    }
}
