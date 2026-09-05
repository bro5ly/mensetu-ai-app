package com.interviewapp.company;

import com.interviewapp.company.CompanySourceDtos.FetchedSourcePreview;
import com.interviewapp.company.CompanySourceDtos.SourceResponse;
import com.interviewapp.question.QuestionDtos.QuestionResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 会社関連の DTO 群。 */
public final class CompanyDtos {

    private CompanyDtos() {
    }

    public record CreateCompanyRequest(
            @NotBlank @Size(max = 255) String name,
            String overview,
            /** 企業リサーチウィザードから会社作成と同時に登録する質問(任意)。 */
            @Size(max = 10) List<@NotBlank String> questions,
            /** 企業リサーチウィザードで既にfetch済みのソース(任意)。作成時に再fetchはしない。 */
            @Size(max = 20) List<FetchedSourcePreview> sources) {
    }

    public record UpdateCompanyRequest(
            @Size(max = 255) String name,
            String overview) {
    }

    /** 企業リサーチ: 会社名＋登録済みソースから企業概要の下書きを生成する。 */
    public record ResearchCompanyRequest(
            @NotBlank @Size(max = 255) String name,
            /** ウィザードのソース添付ステップで既にfetch済みのソース(任意、0件でも可)。 */
            @Size(max = 20) List<FetchedSourcePreview> sources,
            /** 再生成時に土台にする現在の下書き(任意)。 */
            String currentOverview,
            /** 反映してほしい観点(任意)。 */
            String feedback) {
    }

    /** 企業リサーチの結果(未保存)。 */
    public record ResearchDraft(String overview) {
    }

    /** 企業概要から面接想定質問を生成するリクエスト。 */
    public record GenerateQuestionsRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank String overview) {
    }

    /** 生成された質問(未保存)。 */
    public record GeneratedQuestions(List<String> questions) {
    }

    /** サイドバー一覧用。 */
    public record CompanySummary(UUID id, String name, Instant createdAt) {

        public static CompanySummary from(Company c) {
            return new CompanySummary(c.getId(), c.getName(), c.getCreatedAt());
        }
    }

    /** 会社詳細（プロフィール＋質問一覧＋ソース一覧）。 */
    public record CompanyDetail(
            UUID id,
            String name,
            String overview,
            Instant createdAt,
            Instant updatedAt,
            List<QuestionResponse> questions,
            List<SourceResponse> sources) {

        public static CompanyDetail from(
                Company c, List<QuestionResponse> questions, List<SourceResponse> sources) {
            return new CompanyDetail(c.getId(), c.getName(), c.getOverview(),
                    c.getCreatedAt(), c.getUpdatedAt(), questions, sources);
        }
    }
}
