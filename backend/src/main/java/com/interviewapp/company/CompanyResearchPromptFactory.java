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
            - 「・」で始まる箇条書き 3〜5 点、その後に面接傾向を 1〜2 文。全体で 250 文字程度。
            - 検索結果に書かれていないことは断定しない。曖昧なものは「〜とされる」等にする。
            - 「以下にまとめます」「〜について説明します」などの前置き・見出し・締めの文は書かない。1文字目から本文。

            出力例:
            ・若手にも早くから裁量を与える文化がある
            ・チームでの協働と主体性を重視する
            ・顧客との長期的な関係づくりを大切にしている
            面接では、経験を具体的に語れるか、志望動機に一貫性があるかが見られるとされる。
            """;

    private static final String QUESTIONS_SYSTEM = """
            あなたは面接官です。会社情報をもとに、その会社の新卒面接で実際に聞かれそうな質問を3つ作ります。

            出力は「質問文だけ」を3行。番号・記号・説明文・前置き・締めの文は一切書かない。
            各質問は疑問文で終わり、日本語で40文字以内。志望動機・自己PR・経験・逆質問などから選ぶ。

            出力例:
            当社を志望する理由を、具体的なきっかけを交えて教えてください。
            チームで成果を出した経験と、その中でのあなたの役割を教えてください。
            入社後に挑戦したいことは何ですか。
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
