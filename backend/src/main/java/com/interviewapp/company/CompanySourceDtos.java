package com.interviewapp.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
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

    /** 既存の会社にソースをまとめて追加する。URLごとにfetchし、失敗したものはfailedに振り分ける。 */
    public record BatchAddSourcesRequest(@NotEmpty @Size(max = 20) List<@NotBlank String> urls) {
    }

    /** 一括追加のうち失敗した1件(URLとエラーメッセージ)。 */
    public record FailedSource(String url, String message) {
    }

    public record BatchAddSourcesResponse(List<SourceResponse> added, List<FailedSource> failed) {
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
