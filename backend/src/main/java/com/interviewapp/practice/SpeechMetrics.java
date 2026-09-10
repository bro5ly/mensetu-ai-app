package com.interviewapp.practice;

/**
 * ユーザーの音声発話を数値化した特徴({@link SpeechMetricsAnalyzer}が算出)。
 * コーチ役LLMへの参考情報として渡すために使う(ユーザーには見せない、
 * {@code chat_messages}にも保存しない)。
 */
public record SpeechMetrics(double charsPerSecond, Pace pace, int fillerCount, double longestPauseSeconds) {

    /** 話す速さの3段階。日本語の目安(1秒あたりの文字数): 普通6〜7、早口9〜10、遅口5弱。 */
    public enum Pace {
        SLOW,
        NORMAL,
        FAST
    }
}
