package com.interviewapp.company;

import com.interviewapp.company.WebSearchClient.SearchResult;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Component;

/** {@link CompanyResearchLlm} の Spring AI (Ollama) 実装。 */
@Component
public class SpringAiCompanyResearchLlm implements CompanyResearchLlm {

    /** リサーチ要約: 概要文なので出力トークンを絞って CPU 推論の待ち時間を短くする。 */
    private static final OllamaOptions OVERVIEW_OPTIONS =
            OllamaOptions.builder().numPredict(360).temperature(0.4).build();

    /** 質問生成: 3 行だけなのでさらに短く、ぶれないよう温度も低め。 */
    private static final OllamaOptions QUESTIONS_OPTIONS =
            OllamaOptions.builder().numPredict(200).temperature(0.3).build();

    private final ChatClient chatClient;
    private final CompanyResearchPromptFactory prompts;

    public SpringAiCompanyResearchLlm(
            ChatClient.Builder chatClientBuilder, CompanyResearchPromptFactory prompts) {
        this.chatClient = chatClientBuilder.build();
        this.prompts = prompts;
    }

    @Override
    public String summarizeOverview(
            String companyName, List<SearchResult> results, String currentOverview, String feedback) {
        String content = call(
                prompts.overviewSystemPrompt(),
                prompts.overviewUserPrompt(companyName, results, currentOverview, feedback),
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
                prompts.questionsUserPrompt(companyName, overview),
                QUESTIONS_OPTIONS,
                "質問の生成");
        List<String> questions = parseQuestionLines(content);
        if (questions.isEmpty()) {
            throw new IllegalStateException("質問の生成に失敗しました(LLM 応答をパースできず)");
        }
        return questions;
    }

    /** ChatClient 呼び出しの共通化。接続エラー等は {@link IllegalStateException} に変換する(→ 502)。 */
    private String call(String system, String user, OllamaOptions options, String what) {
        try {
            return chatClient.prompt().system(system).user(user).options(options).call().content();
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
