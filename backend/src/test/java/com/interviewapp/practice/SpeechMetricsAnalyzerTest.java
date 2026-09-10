package com.interviewapp.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import com.interviewapp.practice.SpeechMetrics.Pace;
import org.junit.jupiter.api.Test;

class SpeechMetricsAnalyzerTest {

    @Test
    void 標準的な速さで分析できる() {
        // 11文字を1.7秒(約6.5文字/秒)。SLOW(5.0)とFAST(8.5)の間に収まる標準的な速さ。
        SpeechMetrics metrics = SpeechMetricsAnalyzer.analyze("がくせいじだいのはなし", 1.7, 0.0);

        assertThat(metrics.pace()).isEqualTo(Pace.NORMAL);
        assertThat(metrics.charsPerSecond()).isCloseTo(11.0 / 1.7, offset(0.01));
    }

    @Test
    void 速いと判定される() {
        SpeechMetrics metrics = SpeechMetricsAnalyzer.analyze("えーとえーと学生時代の話です", 1.2, 0.0);

        assertThat(metrics.pace()).isEqualTo(Pace.FAST);
    }

    @Test
    void 遅いと判定される() {
        // 6文字を3秒(=2文字/秒)。
        SpeechMetrics metrics = SpeechMetricsAnalyzer.analyze("がくせいじ", 3.0, 0.0);

        assertThat(metrics.pace()).isEqualTo(Pace.SLOW);
    }

    @Test
    void フィラーワードの回数を数える() {
        SpeechMetrics metrics = SpeechMetricsAnalyzer.analyze("えーと、あのー、まあ普通です", 2.0, 0.0);

        assertThat(metrics.fillerCount()).isEqualTo(3);
    }

    @Test
    void durationがnullなら分析できない() {
        assertThat(SpeechMetricsAnalyzer.analyze("テスト", null, 0.0)).isNull();
    }

    @Test
    void durationが0以下なら分析できない() {
        assertThat(SpeechMetricsAnalyzer.analyze("テスト", 0.0, 0.0)).isNull();
        assertThat(SpeechMetricsAnalyzer.analyze("テスト", -1.0, 0.0)).isNull();
    }

    @Test
    void テキストが空なら分析できない() {
        assertThat(SpeechMetricsAnalyzer.analyze("", 2.0, 0.0)).isNull();
        assertThat(SpeechMetricsAnalyzer.analyze(null, 2.0, 0.0)).isNull();
    }

    @Test
    void 標準的で特徴が無ければ注記はnull() {
        SpeechMetrics metrics = SpeechMetricsAnalyzer.analyze("がくせいじだいのはなしです", 2.0, 0.5);

        assertThat(SpeechMetricsAnalyzer.describe(metrics)).isNull();
    }

    @Test
    void metricsがnullなら注記もnull() {
        assertThat(SpeechMetricsAnalyzer.describe(null)).isNull();
    }

    @Test
    void 速い場合は注記に速さが含まれる() {
        SpeechMetrics metrics = SpeechMetricsAnalyzer.analyze("えーとえーと学生時代の話です", 1.2, 0.0);

        assertThat(SpeechMetricsAnalyzer.describe(metrics))
                .contains("速さがやや速め")
                .contains("つなぎ言葉が2回");
    }

    @Test
    void 遅い場合は注記に遅さが含まれる() {
        SpeechMetrics metrics = SpeechMetricsAnalyzer.analyze("がくせいじ", 3.0, 0.0);

        assertThat(SpeechMetricsAnalyzer.describe(metrics)).contains("ゆっくりめ");
    }

    @Test
    void 長い間があれば注記に含まれる() {
        SpeechMetrics metrics = SpeechMetricsAnalyzer.analyze("がくせいじだいのはなしです", 2.0, 2.5);

        assertThat(SpeechMetricsAnalyzer.describe(metrics)).contains("2.5秒ほどの間があった");
    }

    @Test
    void フィラーが1回だけなら注記に含めない() {
        SpeechMetrics metrics = SpeechMetricsAnalyzer.analyze("えーと、普通の速さの話です", 2.0, 0.0);

        assertThat(metrics.fillerCount()).isEqualTo(1);
        assertThat(SpeechMetricsAnalyzer.describe(metrics)).isNull();
    }

    @Test
    void 短い間は注記に含めない() {
        SpeechMetrics metrics = SpeechMetricsAnalyzer.analyze("がくせいじだいのはなしです", 2.0, 1.0);

        assertThat(SpeechMetricsAnalyzer.describe(metrics)).isNull();
    }
}
