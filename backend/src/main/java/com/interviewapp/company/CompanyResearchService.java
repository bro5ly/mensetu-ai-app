package com.interviewapp.company;

import com.interviewapp.company.CompanyDtos.GeneratedQuestions;
import com.interviewapp.company.CompanyDtos.ResearchDraft;
import com.interviewapp.company.CompanySourceDtos.FetchedSourcePreview;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 企業リサーチ(会社追加ウィザード)のロジック。
 *
 * <p>いずれのメソッドも DB には保存しない。会社レコードはウィザード最終ステップの
 * {@code CompanyService.create}(questions・sources 付き)で初めて作られる。ユーザーがウィザードの
 * ソース添付ステップで既にfetch済みのソースをそのまま渡してもらう前提で、それらは再fetchしない。
 * ただし初回検索時は {@link WebSearchClient}(SearXNG)で会社名を自動検索し、見つかったURLを
 * 追加でfetchしてソースに補う(ユーザーが1件もURLを貼らなくても最低限の材料が揃うようにするため)。</p>
 */
@Service
public class CompanyResearchService {

    private static final Logger log = LoggerFactory.getLogger(CompanyResearchService.class);

    /**
     * SearXNGへの検索クエリに付与するキーワード。新卒面接対策の文脈に絞り込みつつ、
     * 業界動向に触れているページも拾えるよう「業界」を含める(業界情報もデフォルトで
     * 企業概要に含める方針のため。プロンプト側の指示は{@link CompanyResearchPromptFactory}参照)。
     */
    private static final String SEARCH_QUERY_SUFFIX = "新卒 採用 面接 社風 業界動向";

    /** SearXNGの検索結果のうち、fetchを試みる候補の件数(上位から)。 */
    private static final int SEARCH_CANDIDATE_LIMIT = 5;

    /** 実際に自動追加するソースの上限。fetchはネットワークI/Oで時間がかかるため絞る。 */
    private static final int AUTO_SOURCE_LIMIT = 3;

    /**
     * 自動検索(SearXNGへの問い合わせ＋複数URLのfetch)を許す最短間隔。フロントの不具合や
     * 連打で自動検索が乱発され、SearXNGや検索結果先の任意のサイトに過度な負荷をかけない
     * ようにするための、アプリ全体で共有するクールダウン。
     */
    private static final Duration AUTO_SEARCH_COOLDOWN = Duration.ofSeconds(10);

    private final CompanyResearchLlm researchLlm;
    private final WebSearchClient webSearchClient;
    private final CompanySourceService companySourceService;
    private final AtomicReference<Instant> lastAutoSearchAt = new AtomicReference<>(Instant.EPOCH);

    public CompanyResearchService(
            CompanyResearchLlm researchLlm, WebSearchClient webSearchClient, CompanySourceService companySourceService) {
        this.researchLlm = researchLlm;
        this.webSearchClient = webSearchClient;
        this.companySourceService = companySourceService;
    }

    /**
     * 会社名＋登録済みソース(＋あれば現在の下書きとフィードバック)から企業概要の下書きを生成する。
     *
     * <p>初回検索時({@code currentOverview}が空、つまりまだ下書きが無い)のみ、SearXNGで会社名を
     * 自動検索してソースを補う。ユーザーが既に確認・編集した下書きを土台にする再検索時は、
     * ユーザーが選んだ情報を優先し追加の自動検索はしない(検索結果が毎回変わって下書きが
     * ぶれるのを避けるため)。ユーザー提供ソースを常に先頭にして優先度を明確にし、
     * 自動検索分は不明点を補う参考情報として末尾に加える。URLが重複するものは追加しない。</p>
     */
    public ResearchDraft research(
            String companyName, List<FetchedSourcePreview> sources, String currentOverview, String feedback) {
        String name = companyName.trim();

        List<FetchedSourcePreview> allSources = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        if (sources != null) {
            for (FetchedSourcePreview s : sources) {
                if (s != null && StringUtils.hasText(s.url()) && seenUrls.add(s.url())) {
                    allSources.add(s);
                }
            }
        }

        if (!StringUtils.hasText(currentOverview)) {
            for (FetchedSourcePreview auto : autoSearchSources(name)) {
                if (seenUrls.add(auto.url())) {
                    allSources.add(auto);
                }
            }
        }

        String overview = researchLlm.summarizeOverview(name, allSources, currentOverview, feedback);
        return new ResearchDraft(overview, allSources);
    }

    /** 前回の自動検索から {@link #AUTO_SEARCH_COOLDOWN} 以上経っていれば、枠を確保してtrueを返す。 */
    private boolean tryAcquireAutoSearchSlot() {
        Instant now = Instant.now();
        Instant previous = lastAutoSearchAt.get();
        if (Duration.between(previous, now).compareTo(AUTO_SEARCH_COOLDOWN) < 0) {
            return false;
        }
        return lastAutoSearchAt.compareAndSet(previous, now);
    }

    /**
     * SearXNGで会社名を検索し、上位候補のうち実際にfetchできた分だけ返す(失敗したURLはスキップ)。
     * クールダウン中(短時間に連続して呼ばれた場合)は検索自体をスキップし、空リストを返す。
     */
    private List<FetchedSourcePreview> autoSearchSources(String companyName) {
        if (!tryAcquireAutoSearchSlot()) {
            log.debug("自動検索のクールダウン中のためスキップします");
            return List.of();
        }

        List<WebSearchClient.SearchResult> results =
                webSearchClient.search(companyName + " " + SEARCH_QUERY_SUFFIX, SEARCH_CANDIDATE_LIMIT);
        List<FetchedSourcePreview> fetched = new ArrayList<>();
        for (WebSearchClient.SearchResult result : results) {
            if (fetched.size() >= AUTO_SOURCE_LIMIT) {
                break;
            }
            try {
                fetched.add(companySourceService.previewFetch(result.url()));
            } catch (RuntimeException e) {
                log.debug("自動検索で見つけたURLのfetchに失敗したためスキップします: {}", result.url());
            }
        }
        return fetched;
    }

    /**
     * 確認済みの企業概要から面接想定質問を 3 つ生成する。
     */
    public GeneratedQuestions generateQuestions(String companyName, String overview) {
        List<String> questions = researchLlm.generateQuestions(companyName.trim(), overview.trim());
        return new GeneratedQuestions(questions);
    }
}
