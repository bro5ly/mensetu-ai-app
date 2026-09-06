package com.interviewapp.company;

import com.interviewapp.company.CompanySourceDtos.FetchedSourcePreview;
import com.interviewapp.llm.OllamaChatClient;
import com.interviewapp.llm.OllamaMessage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** {@link CompanyResearchLlm} の Ollama 実装({@link OllamaChatClient}経由、理由はそちらのJavadoc参照)。 */
@Component
public class SpringAiCompanyResearchLlm implements CompanyResearchLlm {

    /**
     * リサーチ要約: 目標は600〜900文字程度だが、途中で不自然に切れる方が実害が大きいため、
     * 上限(num_predict/num_ctx)は狙いの文字数よりかなり大きめに確保しておく。実際の長さは
     * プロンプト側の指示(文字数目安)に委ね、返ってきた内容はそのまま出力する(こちらで
     * 切り詰めない)。ソース本文(1件あたり最大2000文字)を複数渡すことがあるため、
     * 入力+出力の両方を余裕を持って収められるよう num_ctx も大きめにする。
     */
    private static final Map<String, Object> OVERVIEW_OPTIONS =
            Map.of("num_predict", 4096, "num_ctx", 8192, "temperature", 0.4);

    /** 質問生成: 出力自体は短いが、同じ理由で上限は余裕を持たせる。 */
    private static final Map<String, Object> QUESTIONS_OPTIONS =
            Map.of("num_predict", 800, "num_ctx", 8192, "temperature", 0.3);

    private final OllamaChatClient ollama;
    private final CompanyResearchPromptFactory prompts;
    private final QuestionBankService questionBank;
    private final String model;

    public SpringAiCompanyResearchLlm(
            OllamaChatClient ollama,
            CompanyResearchPromptFactory prompts,
            QuestionBankService questionBank,
            @Value("${spring.ai.ollama.chat.options.model}") String model) {
        this.ollama = ollama;
        this.prompts = prompts;
        this.questionBank = questionBank;
        this.model = model;
    }

    @Override
    public String summarizeOverview(
            String companyName, List<FetchedSourcePreview> sources, String currentOverview, String feedback) {
        String content = call(
                prompts.overviewSystemPrompt(),
                prompts.overviewUserPrompt(companyName, sources, currentOverview, feedback),
                OVERVIEW_OPTIONS,
                "企業概要の生成");
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalStateException("企業概要の生成に失敗しました(LLM から空応答)");
        }
        return trimmed;
    }

    @Override
    public List<String> generateQuestions(String companyName, String overview) {
        String content = call(
                prompts.questionsSystemPrompt(),
                prompts.questionsUserPrompt(companyName, overview, questionBank.sampleForPrompt()),
                QUESTIONS_OPTIONS,
                "質問の生成");
        List<String> questions = parseQuestionLines(content);
        if (questions.isEmpty()) {
            throw new IllegalStateException("質問の生成に失敗しました(LLM 応答をパースできず)");
        }
        return questions;
    }

    /** Ollama 呼び出しの共通化。接続エラー等は {@link IllegalStateException} に変換する(→ 502)。 */
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
     * LLM の生応答を 1 行 1 問としてパースする。番号・記号・空行を落とし、重複を除いて先頭 3 件を返す。
     * package-private: 単体テスト用。
     */
    static List<String> parseQuestionLines(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String line : raw.split("\\R")) {
            String cleaned = line.strip()
                    .replaceFirst("^[-*・•●\\u30fb]\\s*", "")
                    .replaceFirst("^\\(?\\d+[.)、:：]?\\s*", "")
                    .replaceFirst("^[Qq][.)：:]?\\s*", "")
                    .strip();
            if (cleaned.length() >= 5) {
                seen.add(cleaned);
            }
        }
        return new ArrayList<>(seen).subList(0, Math.min(3, seen.size()));
    }
}
