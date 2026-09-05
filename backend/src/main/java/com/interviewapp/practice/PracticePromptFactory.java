package com.interviewapp.practice;

import com.interviewapp.practice.PracticeCoachLlm.Turn;
import com.interviewapp.session.ChatMessage;
import com.interviewapp.session.MessageRole;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 練習モードのシステムプロンプトと会話履歴を組み立てる純粋なファクトリ。
 *
 * <p>プロンプトのルールは CLAUDE.md「練習モード」の決定事項に対応:
 * AI からは会話を終了しない / 詰まったらすぐ具体的なコツと回答例を出す /
 * アドバイス時は冒頭に {@code <<ADVICE>>} を付ける。</p>
 */
@Component
public class PracticePromptFactory {

    static final String ADVICE_MARKER = "<<ADVICE>>";

    /** プロンプト肥大化防止のため、ソース1件あたりの本文をこの文字数までに切り詰める。 */
    private static final int SOURCE_CONTENT_LIMIT = 1500;

    public String systemPrompt(
            String companyName, String companyOverview, List<String> sourceExcerpts, String questionText) {
        return """
            あなたは新卒就活生の面接練習に付き合う面接コーチです。
            対象の会社は「%s」、今回練習する質問は次の1問だけです。
            %s
            質問: %s

            進め方:
            - まず質問に答えてもらい、回答を一緒に深掘りしていきます。
            - あなたから面接を終了してはいけません。ユーザーが終了ボタンを押すまで練習を続けます。
            - 次の質問に勝手に移らず、この質問への回答を良くすることに集中します。
            - ユーザーが回答に詰まったり「わからない」と言ったら、抽象的な励ましで終わらせず、
              すぐに具体的なコツ（話す順番の型など）と、その場で使える回答例を示します。
            - 回答は日本語の話し言葉で、2〜4文程度に簡潔にまとめます。
            - 点数付けや総合評価はしません。

            出力ルール（重要）:
            - コツや回答例など「アドバイス」を返すときは、応答の一番最初に %s と書いてください。
            - 通常の深掘りの質問や相づちには %s を付けないでください。
            """
                .formatted(companyName, companyInfoBlock(companyOverview, sourceExcerpts), questionText,
                        ADVICE_MARKER, ADVICE_MARKER);
    }

    /** 会社概要・関連ソースが無ければ空文字を返し、プロンプトに余計な見出しを残さない。 */
    private String companyInfoBlock(String companyOverview, List<String> sourceExcerpts) {
        boolean hasOverview = StringUtils.hasText(companyOverview);
        boolean hasSources = sourceExcerpts != null && !sourceExcerpts.isEmpty();
        if (!hasOverview && !hasSources) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n企業情報:\n");
        if (hasOverview) {
            sb.append(companyOverview.trim()).append("\n");
        }
        if (hasSources) {
            for (String excerpt : sourceExcerpts) {
                String trimmed = excerpt == null ? "" : excerpt.trim();
                if (trimmed.length() > SOURCE_CONTENT_LIMIT) {
                    trimmed = trimmed.substring(0, SOURCE_CONTENT_LIMIT);
                }
                sb.append("- ").append(trimmed).append("\n");
            }
        }
        return sb.toString();
    }

    /** 保存済みメッセージ（古い順）を LLM 履歴に変換する。マーカーは既に除去済みの content を使う。 */
    public List<Turn> toHistory(List<ChatMessage> messages) {
        return messages.stream()
                .map(m -> new Turn(
                        m.getRole() == MessageRole.ASSISTANT ? Turn.Role.ASSISTANT : Turn.Role.USER,
                        m.getContent()))
                .toList();
    }
}
