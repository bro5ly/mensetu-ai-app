package com.interviewapp.practice;

import com.interviewapp.session.MessageType;
import org.springframework.stereotype.Component;

/**
 * 練習モードのアシスタント応答冒頭に付く {@code <<ADVICE>>} マーカーを検出し、本文から取り除く純粋なパーサ。
 *
 * <p>本番モードの終了検知（{@code <<INTERVIEW_END>>}）と同じマーカー方式に揃えるための基盤クラス。
 * WebSocket ハンドラから切り離してあり、単体テストしやすいよう副作用を持たない。</p>
 */
@Component
public class AssistantMarkerParser {

    static final String ADVICE_MARKER = "<<ADVICE>>";

    /**
     * 応答全文（またはストリーミング先頭で十分な長さが溜まったバッファ）を受け取り、
     * メッセージ種別とマーカー除去済み本文を返す。
     *
     * @param rawResponse LLM の生応答。null は空文字として扱う
     */
    public Result parse(String rawResponse) {
        if (rawResponse == null) {
            return new Result(MessageType.NORMAL, "");
        }
        String leadingTrimmed = rawResponse.stripLeading();
        if (leadingTrimmed.startsWith(ADVICE_MARKER)) {
            String body = leadingTrimmed.substring(ADVICE_MARKER.length()).stripLeading();
            return new Result(MessageType.ADVICE, body);
        }
        return new Result(MessageType.NORMAL, rawResponse.strip());
    }

    /**
     * ストリーミング先頭チャンクだけでマーカー有無を判定できるか。
     * まだ {@code <<ADVICE>>} の一部かもしれない短いプレフィックスの場合は false。
     */
    public boolean canDecideFrom(String bufferedPrefix) {
        if (bufferedPrefix == null) {
            return false;
        }
        String trimmed = bufferedPrefix.stripLeading();
        if (trimmed.length() >= ADVICE_MARKER.length()) {
            return true;
        }
        return !ADVICE_MARKER.startsWith(trimmed);
    }

    /** パース結果。 */
    public record Result(MessageType messageType, String content) {
    }
}
