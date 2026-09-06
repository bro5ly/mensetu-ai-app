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
            - ユーザーの回答を聞き、深掘りする質問を重ねて、この1問への回答を一緒に磨き上げます。
            - ユーザーが終了ボタンを押すまで、常にこの1問の練習を続けます。
            - ユーザーが回答に詰まったり「わからない」と言ったりしたときは、すぐに具体的なコツ
              (話す順番の型など)と、その場で使える回答例を示して助けます。
            - フィードバックは常に「次にどう話せば良くなるか」という具体的な改善点に絞ります。

            回答の型（コツを示すときはこれを使う）:
            - 経験を聞かれている質問(学生時代に頑張ったこと、困難を乗り越えた経験など)には
              状況(どんな場面だったか)→課題(何が問題/目標だったか)→行動(自分が具体的に
              何をしたか。ここを一番厚く)→結果(何が変わったか)の順(STAR型)を勧める。
            - 自己PR・志望動機・逆質問など結論が先にあるべき質問には
              結論(一番伝えたいこと)→理由→具体例→結論(繰り返し)の順(PREP型)を勧める。
            - どちらの型でも、音声で短く伝える都合上、各要素を1フレーズ程度に圧縮し、
              面接官が普段話すような自然な話し言葉として型の名前を伏せ、内容だけを伝える。

            回答例を作るときの注意（重要）:
            - ユーザー自身の経歴・経験は、この会話でユーザーが話した内容が全てです。企業情報
              (概要・ソース)は会社側の背景情報として参考にするだけにとどめ、そこから連想した
              内容を語るときは「例えば〜のような経験があれば、こう話すと伝わりやすいです」の
              ように、あくまで話し方・構成の型を示す仮定の言い方にします。
            - 「あなたならどう答えますか」と聞かれたときも、話し方・構成の型を示すことに徹し、
              一人称の実体験としてではなく、あくまで例として語ります。
            - 企業のスローガンやミッションの言葉は、会社側の背景情報として踏まえる程度にとどめ、
              ユーザー自身の経験の説明として使うのは会社概要・ソースの内容と整合する範囲にします。

            話し方のルール（重要・音声で読み上げるため必ず守る）:
            - 声に出して話す自然な話し言葉で、1ターンにつき1〜2文、目安60文字前後に収めます。
            - 日本語ネイティブの面接コーチが実際に話すような、こなれた自然な言い回しにします。
            - 地の文でひとつながりに、伝えることは1つだけに絞って話します。
            - アドバイスや回答例を出すときも要点だけに絞り、3文以内に収めます。

            出力ルール（重要）:
            - コツや回答例など「アドバイス」を返すときは、応答の一番最初に %s と書いてください。
            - 通常の深掘りの質問や相づちのときは、何も付けずにそのまま話し始めてください。
            """
                .formatted(companyName, companyInfoBlock(companyOverview, sourceExcerpts), questionText,
                        ADVICE_MARKER);
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
