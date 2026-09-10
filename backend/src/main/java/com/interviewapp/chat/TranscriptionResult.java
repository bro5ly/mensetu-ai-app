package com.interviewapp.chat;

import java.util.List;

/**
 * STT の文字起こし結果。テキストに加えて、発話全体の長さ({@code durationSeconds})と
 * 発話区間({@code segments}、VAD で無音を除いた後の区間)を持つ。
 *
 * <p>{@code durationSeconds}/{@code segments} は {@code response_format=verbose_json} を
 * サポートしないSTTサーバーからは返らないことがあるため null / 空リストになりうる
 * ({@link SpeechMetricsAnalyzer 呼び出し側}はその場合、話速などの分析をスキップする)。</p>
 */
public record TranscriptionResult(String text, Double durationSeconds, List<Segment> segments) {

    public static TranscriptionResult empty() {
        return new TranscriptionResult("", null, List.of());
    }

    /** テキストのみ分かっている場合(タイムスタンプ情報が無い場合)のファクトリ。 */
    public static TranscriptionResult textOnly(String text) {
        return new TranscriptionResult(text, null, List.of());
    }

    /**
     * 発話区間同士の最大の間(秒)。区間が2つ未満なら0。
     * 言い淀み・沈黙による「間」の検知に使う。
     */
    public double longestGapSeconds() {
        if (segments == null || segments.size() < 2) {
            return 0.0;
        }
        double max = 0.0;
        for (int i = 1; i < segments.size(); i++) {
            double gap = segments.get(i).start() - segments.get(i - 1).end();
            if (gap > max) {
                max = gap;
            }
        }
        return max;
    }

    /** Whisper の1発話区間(VAD後)。 */
    public record Segment(double start, double end) {
    }
}
