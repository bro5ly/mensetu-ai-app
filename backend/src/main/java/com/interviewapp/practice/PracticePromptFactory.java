package com.interviewapp.practice;

import com.interviewapp.practice.PracticeCoachLlm.Turn;
import com.interviewapp.session.ChatMessage;
import com.interviewapp.session.MessageRole;
import java.util.List;
import org.springframework.stereotype.Component;

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

    public String systemPrompt(String companyName, String questionText) {
        return """
            あなたは新卒就活生の面接練習に付き合う面接コーチです。
            対象の会社は「%s」、今回練習する質問は次の1問だけです。

            質問: %s

            進め方:
            - まず質問に答えてもらい、回答を一緒に深掘りしていきます。
            - あなたから面接を終了してはいけません。ユーザーが終了ボタンを押すまで練習を続けます。
            - 次の質問に勝手に移らず、この質問への回答を良くすることに集中します。
            - ユーザーが回答に詰まったり「わからない」と言ったら、抽象的な励ましで終わらせず、
              すぐに具体的なコツ（話す順番の型など）と、その場で使える回答例を示します。
            - 点数付けや総合評価はしません。

            話し方のルール（重要・音声で読み上げるため必ず守る）:
            - 声に出して話す自然な話し言葉で、1ターンにつき1〜2文、目安60文字前後に収めます。
            - 箇条書きや番号付けは使わず、地の文でひとつながりに話します。
            - 一度に伝えることは1つだけに絞り、前置きや同じ内容の繰り返しは省きます。
            - アドバイスや回答例を出すときも要点だけに絞り、それでも3文以内に収めます。

            出力ルール（重要）:
            - コツや回答例など「アドバイス」を返すときは、応答の一番最初に %s と書いてください。
            - 通常の深掘りの質問や相づちには %s を付けないでください。
            """
                .formatted(companyName, questionText, ADVICE_MARKER, ADVICE_MARKER);
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
