package com.interviewapp.question;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** 質問関連の DTO 群。 */
public final class QuestionDtos {

    private QuestionDtos() {
    }

    public record CreateQuestionRequest(
            @NotBlank String questionText,
            @Size(max = 50) String internalCategory,
            Integer displayOrder) {
    }

    public record UpdateQuestionRequest(
            String questionText,
            @Size(max = 50) String internalCategory,
            Integer displayOrder) {
    }

    public record QuestionResponse(
            UUID id,
            UUID companyId,
            String questionText,
            String internalCategory,
            int displayOrder,
            Instant createdAt) {

        public static QuestionResponse from(InterviewQuestion q) {
            return new QuestionResponse(q.getId(), q.getCompanyId(), q.getQuestionText(),
                    q.getInternalCategory(), q.getDisplayOrder(), q.getCreatedAt());
        }
    }
}
