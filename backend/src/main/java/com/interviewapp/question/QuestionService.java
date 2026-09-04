package com.interviewapp.question;

import com.interviewapp.common.NotFoundException;
import com.interviewapp.question.QuestionDtos.CreateQuestionRequest;
import com.interviewapp.question.QuestionDtos.QuestionResponse;
import com.interviewapp.question.QuestionDtos.UpdateQuestionRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class QuestionService {

    private final InterviewQuestionRepository questionRepository;

    public QuestionService(InterviewQuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> listByCompany(UUID companyId) {
        return questionRepository.findByCompanyIdOrderByDisplayOrderAscCreatedAtAsc(companyId).stream()
                .map(QuestionResponse::from)
                .toList();
    }

    /** 会社の存在チェックは呼び出し側（Controller で CompanyService 経由）で行う前提。 */
    public QuestionResponse add(UUID companyId, CreateQuestionRequest request) {
        int order = request.displayOrder() != null
                ? request.displayOrder()
                : (int) questionRepository.countByCompanyId(companyId);
        InterviewQuestion question = new InterviewQuestion(
                companyId, request.questionText().trim(), request.internalCategory(), order);
        return QuestionResponse.from(questionRepository.save(question));
    }

    public QuestionResponse update(UUID questionId, UpdateQuestionRequest request) {
        InterviewQuestion question = findOrThrow(questionId);
        if (StringUtils.hasText(request.questionText())) {
            question.setQuestionText(request.questionText().trim());
        }
        if (request.internalCategory() != null) {
            question.setInternalCategory(request.internalCategory());
        }
        if (request.displayOrder() != null) {
            question.setDisplayOrder(request.displayOrder());
        }
        return QuestionResponse.from(question);
    }

    public void delete(UUID questionId) {
        InterviewQuestion question = findOrThrow(questionId);
        questionRepository.delete(question);
    }

    @Transactional(readOnly = true)
    public InterviewQuestion findOrThrow(UUID questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new NotFoundException("質問が見つかりません: " + questionId));
    }
}
