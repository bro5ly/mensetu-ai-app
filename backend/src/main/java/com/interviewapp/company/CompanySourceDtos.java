package com.interviewapp.company;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

/** 企業ソース関連の DTO 群。 */
public final class CompanySourceDtos {

    private CompanySourceDtos() {
    }

    /** ウィザードのソース添付ステップ: URLをfetchするだけで保存はしない。 */
    public record FetchSourceRequest(@NotBlank String url) {
    }

    /** fetch結果の下書き(未保存)。ウィザードはこれをそのまま会社作成リクエストに含める。 */
    public record FetchedSourcePreview(String url, String title, String content) {

        public static FetchedSourcePreview from(String url, UrlContentFetcher.ExtractedContent extracted) {
            return new FetchedSourcePreview(url, extracted.title(), extracted.content());
        }
    }

    /** 既存の会社にソースを1件追加する。 */
    public record CreateSourceRequest(@NotBlank String url) {
    }

    public record SourceResponse(
            UUID id, UUID companyId, String url, String title, String content, Instant fetchedAt, Instant createdAt) {

        public static SourceResponse from(CompanySource s) {
            return new SourceResponse(
                    s.getId(), s.getCompanyId(), s.getUrl(), s.getTitle(), s.getContent(),
                    s.getFetchedAt(), s.getCreatedAt());
        }
    }
}
