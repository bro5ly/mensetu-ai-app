package com.interviewapp.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.interviewapp.company.UrlContentFetcher.ExtractedContent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/**
 * HTML→テキスト抽出部分のみを対象にする(実ネットワークなし)。
 * {@link JsoupUrlContentFetcher#fetch} 自体の実HTTP呼び出しは、他のRESTクライアント
 * (WhisperSttClient/VoicevoxTtsClient)と同様に単体テスト対象外とする。
 */
class JsoupUrlContentFetcherTest {

    @Test
    void タイトルと本文を抽出する() {
        Document doc = Jsoup.parse(
                "<html><head><title>採用ページ</title></head><body><p>若手に裁量がある会社です。</p></body></html>");

        ExtractedContent extracted = JsoupUrlContentFetcher.extract(doc);

        assertThat(extracted.title()).isEqualTo("採用ページ");
        assertThat(extracted.content()).contains("若手に裁量がある会社です。");
    }

    @Test
    void タイトルが無ければnull() {
        Document doc = Jsoup.parse("<html><body><p>本文だけ</p></body></html>");

        ExtractedContent extracted = JsoupUrlContentFetcher.extract(doc);

        assertThat(extracted.title()).isNull();
        assertThat(extracted.content()).contains("本文だけ");
    }

    @Test
    void NUL文字を取り除く() {
        // PostgresのTEXT/VARCHARはNUL文字を拒否するため、DBへ渡す前に取り除く必要がある
        // (スクレイピングしたHTMLに稀に紛れ込む)。
        String nul = String.valueOf((char) 0);
        Document doc = Jsoup.parse(
                "<html><head><title>採用ページ</title></head><body><p>本文" + nul + "です。</p></body></html>");

        ExtractedContent extracted = JsoupUrlContentFetcher.extract(doc);

        assertThat(extracted.content()).doesNotContain(nul);
        assertThat(extracted.content()).contains("本文です。");
    }

    @Test
    void 本文が空なら例外() {
        Document doc = Jsoup.parse("<html><head><title>空ページ</title></head><body></body></html>");

        assertThatThrownBy(() -> JsoupUrlContentFetcher.extract(doc))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("本文を抽出できませんでした");
    }
}
