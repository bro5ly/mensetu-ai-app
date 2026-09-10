package com.interviewapp.mock;

import java.util.List;

/**
 * 本番模擬面接で使う、1ターンの会話以外のLLM呼び出しを抽象化するインターフェース。
 * 1ターンごとの面接官応答は既存の {@code practice.PracticeCoachLlm}(汎用のチャット
 * ストリーミング抽象)をそのまま再利用するため、ここには含めない。
 *
 * <p>Spring AI への依存をこの境界に閉じ込め、{@code MockSessionService} を
 * 単体テストしやすくする({@code CompanyResearchLlm} と同じ方針)。</p>
 */
public interface MockInterviewLlm {

    /**
     * 面接全体の会話(質問ごとにまとめた回答)から、スコア・傾向分析・質問ごとの
     * 具体的フィードバックをまとめて生成する(1回のLLM呼び出し。質問ごとの並列評価はしない)。
     */
    ReportResult generateReport(String companyName, String companyOverview, List<QuestionTranscript> transcripts);

    /** 1問についての、質問文と実際の会話(ユーザーの回答・面接官の深掘りのやり取り)。 */
    record QuestionTranscript(String questionText, String conversation) {
    }

    /** 質問ごとの具体的フィードバック。 */
    record QuestionFeedback(String questionText, String userAnswerSummary, String feedback) {
    }

    /** レポート生成結果。 */
    record ReportResult(int score, String tendencyAnalysis, List<QuestionFeedback> feedbacks) {
    }
}
