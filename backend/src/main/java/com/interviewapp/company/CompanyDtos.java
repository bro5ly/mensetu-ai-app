package com.interviewapp.company;

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
            String overview) {
    }

    public record UpdateCompanyRequest(
            @Size(max = 255) String name,
            String overview) {
    }

    /** サイドバー一覧用。 */
    public record CompanySummary(UUID id, String name, Instant createdAt) {

        public static CompanySummary from(Company c) {
            return new CompanySummary(c.getId(), c.getName(), c.getCreatedAt());
        }
    }

    /** 会社詳細（プロフィール＋質問一覧）。 */
    public record CompanyDetail(
            UUID id,
            String name,
            String overview,
            Instant createdAt,
            Instant updatedAt,
            List<QuestionResponse> questions) {

        public static CompanyDetail from(Company c, List<QuestionResponse> questions) {
            return new CompanyDetail(c.getId(), c.getName(), c.getOverview(),
                    c.getCreatedAt(), c.getUpdatedAt(), questions);
        }
    }
}
