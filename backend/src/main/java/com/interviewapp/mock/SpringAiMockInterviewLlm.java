package com.interviewapp.mock;

import com.interviewapp.llm.OllamaChatClient;
import com.interviewapp.llm.OllamaMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@link MockInterviewLlm} の Ollama 実装({@code com.interviewapp.llm.OllamaChatClient}経由、
 * 理由は{@code practice.SpringAiPracticeCoachLlm}のJavadoc参照)。
 */
@Component
public class SpringAiMockInterviewLlm implements MockInterviewLlm {

    /** レポート生成: スコア・傾向分析・質問数分のフィードバックをまとめて出力するため大きめに確保する。 */
    private static final Map<String, Object> REPORT_OPTIONS =
            Map.of("num_predict", 2048, "num_ctx", 8192, "temperature", 0.4);

    private static final Pattern SCORE_PATTERN = Pattern.compile("(?i)SCORE\\s*[:：]\\s*(-?\\d+).*");
    private static final Pattern TENDENCY_PATTERN = Pattern.compile("(?i)TENDENCY\\s*[:：]\\s*(.+)");
    private static final Pattern Q_PATTERN = Pattern.compile("(?i)Q(\\d+)\\s*[:：]\\s*(.+)");
    private static final Pattern A_PATTERN = Pattern.compile("(?i)A(\\d+)\\s*[:：]\\s*(.+)");
    private static final Pattern F_PATTERN = Pattern.compile("(?i)F(\\d+)\\s*[:：]\\s*(.+)");

    private final OllamaChatClient ollama;
    private final MockInterviewPromptFactory prompts;
    private final String model;

    public SpringAiMockInterviewLlm(
            OllamaChatClient ollama,
            MockInterviewPromptFactory prompts,
            @Value("${spring.ai.ollama.chat.options.model}") String model) {
        this.ollama = ollama;
        this.prompts = prompts;
        this.model = model;
    }

    @Override
    public ReportResult generateReport(String companyName, String companyOverview, List<QuestionTranscript> transcripts) {
        String content = call(
                prompts.reportSystemPrompt(), prompts.reportUserPrompt(companyName, companyOverview, transcripts),
                REPORT_OPTIONS, "面接レポートの生成");
        ReportResult result = parseReport(content);
        if (result == null) {
            throw new IllegalStateException("面接レポートの生成に失敗しました(LLM 応答をパースできず)");
        }
        return result;
    }

    private String call(String system, String user, Map<String, Object> options, String what) {
        try {
            List<OllamaMessage> messages = List.of(OllamaMessage.system(system), OllamaMessage.user(user));
            return ollama.call(model, messages, options);
        } catch (IllegalStateException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    what + "に失敗しました。Ollama が起動しているか確認してください。", e);
        }
    }

    /**
     * {@code SCORE:}/{@code TENDENCY:}/{@code Q1:}/{@code A1:}/{@code F1:}...形式の生応答をパースする。
     * score・tendency のどちらかが取れなければ全体を失敗(null)として扱う。質問ごとの行が一部
     * 欠けている場合は、その質問だけ結果から除く(feedbackが無い問いは含めない)。
     * package-private: 単体テスト用。
     */
    static ReportResult parseReport(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Integer score = null;
        String tendency = null;
        Map<Integer, String> questions = new TreeMap<>();
        Map<Integer, String> answers = new TreeMap<>();
        Map<Integer, String> feedbacks = new TreeMap<>();

        for (String line : raw.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            Matcher m;
            if ((m = SCORE_PATTERN.matcher(trimmed)).matches()) {
                score = parseIntSafe(m.group(1));
            } else if ((m = TENDENCY_PATTERN.matcher(trimmed)).matches()) {
                tendency = m.group(1).strip();
            } else if ((m = Q_PATTERN.matcher(trimmed)).matches()) {
                questions.put(Integer.parseInt(m.group(1)), m.group(2).strip());
            } else if ((m = A_PATTERN.matcher(trimmed)).matches()) {
                answers.put(Integer.parseInt(m.group(1)), m.group(2).strip());
            } else if ((m = F_PATTERN.matcher(trimmed)).matches()) {
                feedbacks.put(Integer.parseInt(m.group(1)), m.group(2).strip());
            }
        }
        if (score == null || tendency == null) {
            return null;
        }

        List<QuestionFeedback> result = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : questions.entrySet()) {
            String feedback = feedbacks.get(entry.getKey());
            if (feedback == null || feedback.isBlank()) {
                continue;
            }
            String answer = answers.get(entry.getKey());
            result.add(new QuestionFeedback(entry.getValue(), answer, feedback));
        }
        return new ReportResult(score, tendency, result);
    }

    private static Integer parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
