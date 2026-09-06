package com.interviewapp.company;

import com.interviewapp.company.CompanySourceDtos.FetchedSourcePreview;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 企業リサーチのプロンプトを組み立てる純粋なファクトリ({@code practice.PracticePromptFactory} と同じ方式)。
 */
@Component
public class CompanyResearchPromptFactory {

    /** プロンプト肥大化防止のため、ソース1件あたりの本文をこの文字数までに切り詰める。 */
    private static final int SOURCE_CONTENT_LIMIT = 2000;

    private static final String OVERVIEW_SYSTEM = """
            あなたは就活生の面接対策を助けるリサーチャーです。
            与えられたソース(ユーザーが登録したWebページの本文)をもとに、会社の「企業概要・社風・面接の傾向」を日本語で詳しくまとめます。

            ルール:
            - 「・」で始まる箇条書き 6〜10 点、その後に面接傾向を 3〜5 文程度でまとめる。
              全体で 600〜900 文字程度を目安にし、情報量を優先して省略しすぎない。
            - 箇条書きのうち1〜2点は、その会社が属する業界の動向・特徴(市場環境、業界内での
              位置づけ、業界特有の面接で見られる観点など)に触れる。ソースに業界の情報が
              無ければ、一般的に知られている業界動向を「〜とされる」等の言い回しで補ってよい
              (企業固有の事実と違って、業界の一般的傾向は多少の一般知識での補完を許容する)。
            - ソースに明記されている企業固有の事実は断定的に書き、そう書かれていない一般的な
              推測を補うときは「〜とされる」等の言い回しにする。
            - ソースは登録された順に並んでいる。先頭に近いものほどユーザー自身が選んで登録した
              情報で、後ろの方は補足のために自動検索で見つかった情報。内容が食い違う・
              どちらを信じるべきか迷う場合は、ユーザーが登録した情報(先頭側)を優先する。
            - 1文字目から箇条書きの1点目を直接書き始める。
            - 英語ソースの内容も含めて、日本語ネイティブが書くようなこなれた自然な文章に
              書き直す。

            出力例:
            ・若手にも早くから裁量を与える文化があり、入社1〜2年目でも重要なプロジェクトを任されることがある
            ・チームでの協働と主体性を重視し、個人プレーよりも周囲を巻き込む動き方が評価されやすい
            ・顧客との長期的な関係づくりを大切にしており、短期的な成果よりも信頼構築を重視する傾向がある
            ・社内の風通しが良く、役職に関係なく意見を言いやすい雰囲気があるとされる
            ・研修制度が充実しており、入社後のキャッチアップを支援する仕組みが整っている
            ・成果だけでなく、挑戦する姿勢やプロセスも評価対象になりやすい
            ・業界内では競合との差別化が進んでおり、変化の速さに対応できる人材が求められているとされる
            面接では、これまでの経験を具体的なエピソードとともに語れるかが重視される。抽象的な自己PRよりも、
            行動の背景や工夫した点まで踏み込んで話せるかを見られているとされる。また、志望動機に一貫性があるか、
            入社後のキャリアイメージが会社の事業内容と自然に結びついているかも確認される傾向がある。逆質問の
            場面では、受け身な姿勢ではなく、事前に調べた内容を踏まえた具体的な質問ができるかも見られているとされる。
            """;

    private static final String QUESTIONS_SYSTEM = """
            あなたは面接官です。会社情報と、定番の質問パターンを参考にして、その会社の新卒面接で
            実際に聞かれそうな質問を3つ作ります。

            出力は質問文だけを3行、1行に1問で書く。
            各質問は疑問文で終わり、日本語で40文字以内。志望動機・自己PR・経験・逆質問などから選ぶ。
            実際の面接官が話すような、自然でこなれた日本語にする。

            質問の作り方(重要):
            - 会社情報は「どんな種類の経験・観点を聞くか」を選ぶための参考材料として使い、
              質問文自体は一般的な面接官が使うような普通の言い回しで書く。結果として答えが
              その企業理念に沿うのは自然だが、質問文にスローガンや企業理念のフレーズを
              そのまま埋め込むのは避ける。
            - 定番の質問パターンを土台に、会社の傾向(挑戦を後押しする、チームワークを重視する
              等)に合わせて聞く経験の種類を選び、自然な言い回しに言い換える。

            悪い例(企業理念のフレーズを無理やり質問文に詰め込んでいる):
            「枠にとらわれない創造性」と「高い技術力」の両方を備え、業界初のような挑戦を行った経験はありますか。

            良い例(同じ傾向を踏まえつつ、自然な言い回しにしている):
            既存のやり方にとらわれず、新しい発想で課題を解決した経験を教えてください。

            出力例:
            当社を志望する理由を、具体的なきっかけを交えて教えてください。
            チームで成果を出した経験と、その中でのあなたの役割を教えてください。
            入社後に挑戦したいことは何ですか。
            """;

    public String overviewSystemPrompt() {
        return OVERVIEW_SYSTEM;
    }

    public String overviewUserPrompt(
            String companyName, List<FetchedSourcePreview> sources, String currentOverview, String feedback) {
        StringBuilder sb = new StringBuilder();
        sb.append("会社名: ").append(companyName).append("\n\n");

        sb.append("登録されたソース:\n");
        if (sources == null || sources.isEmpty()) {
            sb.append("(ソースは登録されていません。一般的な新卒面接の観点で補ってください)\n");
        } else {
            for (FetchedSourcePreview s : sources) {
                String title = StringUtils.hasText(s.title()) ? s.title() : s.url();
                String content = s.content() == null ? "" : s.content().trim();
                if (content.length() > SOURCE_CONTENT_LIMIT) {
                    content = content.substring(0, SOURCE_CONTENT_LIMIT);
                }
                sb.append("- ").append(title).append(": ").append(content).append("\n");
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

    public String questionsUserPrompt(String companyName, String overview, List<QuestionBankEntry> bankSample) {
        StringBuilder sb = new StringBuilder();
        sb.append("会社名: ").append(companyName).append("\n\n");
        sb.append("会社情報:\n").append(overview == null ? "" : overview.trim()).append("\n");

        if (bankSample != null && !bankSample.isEmpty()) {
            sb.append("\n定番の質問パターン(参考。表現を会社情報に合わせて言い換え、自然な質問にする):\n");
            for (QuestionBankEntry entry : bankSample) {
                sb.append("- ").append(entry.getQuestionText())
                        .append("(").append(entry.getAnswerGuidance()).append(")\n");
            }
        }

        sb.append("\nこの会社の面接で聞かれそうな質問を 3 つ、1 行に 1 問で出力してください。");
        return sb.toString();
    }
}
