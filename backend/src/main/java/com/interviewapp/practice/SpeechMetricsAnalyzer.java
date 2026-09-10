package com.interviewapp.practice;

import com.interviewapp.practice.SpeechMetrics.Pace;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文字起こし結果とその発話の長さから、話速・フィラーワード・間(ポーズ)を分析する純粋関数。
 *
 * <p>日本語の話す速さの目安(1秒あたりの文字数): 普通6〜7文字、早口9〜10文字、遅口5文字弱
 * (話し方教室等で一般的に使われる目安値)。この目安を元に3段階(SLOW/NORMAL/FAST)へ分類する。</p>
 *
 * <p>分析結果はコーチ役LLMへの参考情報として渡すだけに使う(ユーザーには見せず、
 * {@code chat_messages}にも保存しない)。{@link #describe(SpeechMetrics)}で
 * LLMに渡す自然言語の注記に変換する。</p>
 */
public final class SpeechMetricsAnalyzer {

    private static final double SLOW_THRESHOLD = 5.0;
    private static final double FAST_THRESHOLD = 8.5;
    /** これ以上長い無音区間があれば「間」として言及する目安(不自然な言い淀みの目安)。 */
    private static final double NOTABLE_PAUSE_SECONDS = 2.0;
    /** これ以上フィラーワードがあれば言及する目安。 */
    private static final int NOTABLE_FILLER_COUNT = 2;

    /** 代表的な日本語のフィラーワード(つなぎ言葉)。完全な検出ではなく大まかな目安として使う。 */
    private static final Pattern FILLER_PATTERN =
            Pattern.compile("えーと|ええと|えっと|あのー|そのー|うーんと|うーん|まあ|なんか");

    private SpeechMetricsAnalyzer() {
    }

    /**
     * 話速・フィラーワード・間を分析する。{@code durationSeconds}が取れない(null/0以下)場合や
     * テキストが空の場合は、話速の算出根拠が無いため分析できず null を返す。
     */
    public static SpeechMetrics analyze(String text, Double durationSeconds, double longestPauseSeconds) {
        if (text == null || text.isBlank() || durationSeconds == null || durationSeconds <= 0) {
            return null;
        }
        double charsPerSecond = text.length() / durationSeconds;
        Pace pace;
        if (charsPerSecond >= FAST_THRESHOLD) {
            pace = Pace.FAST;
        } else if (charsPerSecond <= SLOW_THRESHOLD) {
            pace = Pace.SLOW;
        } else {
            pace = Pace.NORMAL;
        }
        return new SpeechMetrics(charsPerSecond, pace, countFillers(text), longestPauseSeconds);
    }

    private static int countFillers(String text) {
        Matcher matcher = FILLER_PATTERN.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * LLMに渡す自然言語の注記を作る。話速・フィラー・間のいずれも目立った特徴が無ければ、
     * 注記自体を付けずに null を返す(毎ターン何か言及されると煩わしいため)。
     */
    public static String describe(SpeechMetrics metrics) {
        if (metrics == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (metrics.pace() == Pace.FAST) {
            sb.append(String.format("話す速さがやや速め(1秒あたり約%.1f文字)。", metrics.charsPerSecond()));
        } else if (metrics.pace() == Pace.SLOW) {
            sb.append(String.format("話す速さがゆっくりめ(1秒あたり約%.1f文字)。", metrics.charsPerSecond()));
        }
        if (metrics.fillerCount() >= NOTABLE_FILLER_COUNT) {
            sb.append(String.format("「えーと」「あの」等のつなぎ言葉が%d回ほど混ざっている。", metrics.fillerCount()));
        }
        if (metrics.longestPauseSeconds() >= NOTABLE_PAUSE_SECONDS) {
            sb.append(String.format("話の途中に%.1f秒ほどの間があった。", metrics.longestPauseSeconds()));
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
