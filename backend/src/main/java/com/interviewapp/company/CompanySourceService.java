package com.interviewapp.company;

import com.interviewapp.common.NotFoundException;
import com.interviewapp.company.CompanySourceDtos.FetchedSourcePreview;
import com.interviewapp.company.CompanySourceDtos.SourceResponse;
import com.interviewapp.company.UrlContentFetcher.ExtractedContent;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会社に紐づくソース(URL登録→サーバーが直接fetchして本文抽出したもの)の登録・取得・削除を扱う。
 */
@Service
@Transactional
public class CompanySourceService {

    private final CompanySourceRepository sourceRepository;
    private final UrlContentFetcher urlContentFetcher;

    public CompanySourceService(CompanySourceRepository sourceRepository, UrlContentFetcher urlContentFetcher) {
        this.sourceRepository = sourceRepository;
        this.urlContentFetcher = urlContentFetcher;
    }

    /** ウィザードのソース添付ステップ用: fetchするだけで保存しない。 */
    @Transactional(readOnly = true)
    public FetchedSourcePreview previewFetch(String url) {
        ExtractedContent extracted = urlContentFetcher.fetch(url.trim());
        return FetchedSourcePreview.from(url.trim(), extracted);
    }

    /** 既存の会社にソースを1件追加する(fetch + 永続化)。 */
    public SourceResponse addSource(UUID companyId, String url) {
        ExtractedContent extracted = urlContentFetcher.fetch(url.trim());
        CompanySource source = new CompanySource(companyId, url.trim(), extracted.title(), extracted.content());
        return SourceResponse.from(sourceRepository.save(source));
    }

    /** ウィザードで既にfetch済みの内容をそのまま永続化する(会社の一括作成時、再fetchはしない)。 */
    public SourceResponse persistFetched(UUID companyId, FetchedSourcePreview preview) {
        CompanySource source = new CompanySource(companyId, preview.url(), preview.title(), preview.content());
        return SourceResponse.from(sourceRepository.save(source));
    }

    @Transactional(readOnly = true)
    public List<SourceResponse> listSources(UUID companyId) {
        return sourceRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(SourceResponse::from)
                .toList();
    }

    public void deleteSource(UUID sourceId) {
        CompanySource source = findOrThrow(sourceId);
        sourceRepository.delete(source);
    }

    /**
     * 練習チャットのプロンプト用: company の登録ソースのうち queryText と関連度が高い上位 limit 件。
     *
     * <p>Postgres拡張(pg_trgm等)や埋め込みモデルには頼らず、2文字bi-gramの重なりで関連度を
     * スコアリングする(日本語は単語境界が無いため word 分割ではなく文字bi-gramを使う)。
     * ソース件数は少数想定(ユーザーが手動で登録)なのでアプリ側での計算で十分。全件0点でも
     * 直近順で上位 limit 件を返す(何も返さないより、ある程度の文脈を渡すほうが有用なため)。</p>
     */
    @Transactional(readOnly = true)
    public List<CompanySource> findRelevant(UUID companyId, String queryText, int limit) {
        List<CompanySource> all = sourceRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        if (all.isEmpty()) {
            return List.of();
        }
        return all.stream()
                .sorted(Comparator.comparingInt(
                        (CompanySource s) -> score(queryText, s.getTitle() + " " + s.getContent())).reversed())
                .limit(limit)
                .toList();
    }

    /** package-private: 単体テスト用。2文字bi-gramの重なり件数をスコアとする。 */
    static int score(String query, String text) {
        if (query == null || query.isBlank() || text == null || text.isBlank()) {
            return 0;
        }
        Set<String> queryGrams = bigrams(query);
        queryGrams.retainAll(bigrams(text));
        return queryGrams.size();
    }

    private static Set<String> bigrams(String s) {
        String normalized = s.toLowerCase();
        Set<String> grams = new HashSet<>();
        for (int i = 0; i + 2 <= normalized.length(); i++) {
            grams.add(normalized.substring(i, i + 2));
        }
        return grams;
    }

    private CompanySource findOrThrow(UUID sourceId) {
        return sourceRepository.findById(sourceId)
                .orElseThrow(() -> new NotFoundException("ソースが見つかりません: " + sourceId));
    }
}
