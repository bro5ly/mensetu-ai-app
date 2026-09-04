package com.interviewapp.company;

import com.interviewapp.company.WebSearchClient.SearchResult;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 企業リサーチのプロンプトを組み立てる純粋なファクトリ({@code practice.PracticePromptFactory} と同じ方式)。
 */
@Component
public class CompanyResearchPromptFactory {

    private static final String OVERVIEW_SYSTEM = """
            あなたは就活生の面接対策を助けるリサーチャーです。
            与えられた Web 検索結果をもとに、会社の「企業概要・社風・面接の傾向」を日本語でまとめます。

            ルール:
            - 箇条書き 3〜5 点と、その後に面接傾向を 1〜2 文でまとめる。
            - 検索結果に書かれていないことは推測で断定しない。曖昧なものは「〜とされる」等の表現にする。
            - 就活生がそのまま面接準備に使える粒度にする。全体で 250 文字程度。
            - 前置き・見出し・「以下にまとめます」等は不要。本文だけを返す。
            """;

    private static final String QUESTIONS_SYSTEM = """
            あなたは面接官です。与えられた会社情報をもとに、この会社の面接で実際に聞かれそうな質問を作ります。

            ルール:
            - ちょうど 3 問。1 行に 1 問。
            - 番号・記号・箇条書き・前置きは付けない。質問文だけを 3 行返す。
            - 新卒面接で自然な、答えに具体性を求める質問にする(志望動機・自己PR・経験・逆質問など)。
            - 日本語。各質問は 40 文字以内程度。
            """;

    public String overviewSystemPrompt() {
        return OVERVIEW_SYSTEM;
    }

    public String overviewUserPrompt(
            String companyName, List<SearchResult> results, String currentOverview, String feedback) {
        StringBuilder sb = new StringBuilder();
        sb.append("会社名: ").append(companyName).append("\n\n");

        sb.append("Web 検索結果:\n");
        if (results == null || results.isEmpty()) {
            sb.append("(有力な検索結果は得られませんでした。一般的な新卒面接の観点で補ってください)\n");
        } else {
            for (SearchResult r : results) {
                sb.append("- ").append(r.title()).append(": ").append(r.snippet()).append("\n");
            }
        }

        if (StringUtils.hasText(currentOverview)) {
            sb.append("\n現在の下書き(これを土台に更新する):\n").append(currentOverview.trim()).append("\n");
        }
        if (StringUtils.hasText(feedback)) {
            sb.append("\nユーザーが特に反映してほしい観点: ").append(feedback.trim()).append("\n");
        }
        sb.append("\n上記をふまえて、企業概要・社風・面接傾向をまとめ直してください。");
        return sb.toString();
    }

    public String questionsSystemPrompt() {
        return QUESTIONS_SYSTEM;
    }

    public String questionsUserPrompt(String companyName, String overview) {
        return """
                会社名: %s

                会社情報:
                %s

                この会社の面接で聞かれそうな質問を 3 つ、1 行に 1 問で出力してください。
                """.formatted(companyName, overview == null ? "" : overview.trim());
    }
}
