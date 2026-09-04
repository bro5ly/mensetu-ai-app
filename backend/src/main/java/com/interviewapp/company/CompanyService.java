package com.interviewapp.company;

import com.interviewapp.common.NotFoundException;
import com.interviewapp.company.CompanyDtos.CompanyDetail;
import com.interviewapp.company.CompanyDtos.CompanySummary;
import com.interviewapp.company.CompanyDtos.CreateCompanyRequest;
import com.interviewapp.company.CompanyDtos.UpdateCompanyRequest;
import com.interviewapp.question.QuestionDtos.CreateQuestionRequest;
import com.interviewapp.question.QuestionDtos.QuestionResponse;
import com.interviewapp.question.QuestionService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final QuestionService questionService;

    public CompanyService(CompanyRepository companyRepository, QuestionService questionService) {
        this.companyRepository = companyRepository;
        this.questionService = questionService;
    }

    @Transactional(readOnly = true)
    public List<CompanySummary> list() {
        return companyRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(CompanySummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyDetail get(UUID companyId) {
        Company company = findOrThrow(companyId);
        return CompanyDetail.from(company, questionService.listByCompany(companyId));
    }

    public CompanyDetail create(CreateCompanyRequest request) {
        Company company = companyRepository.saveAndFlush(new Company(request.name().trim(), request.overview()));

        List<QuestionResponse> questions = List.of();
        if (request.questions() != null && !request.questions().isEmpty()) {
            List<QuestionResponse> created = new ArrayList<>();
            int order = 0;
            for (String text : request.questions()) {
                if (text != null && !text.isBlank()) {
                    created.add(questionService.add(company.getId(),
                            new CreateQuestionRequest(text.trim(), null, order++)));
                }
            }
            questions = created;
        }
        return CompanyDetail.from(company, questions);
    }

    public CompanyDetail update(UUID companyId, UpdateCompanyRequest request) {
        Company company = findOrThrow(companyId);
        if (StringUtils.hasText(request.name())) {
            company.setName(request.name().trim());
        }
        if (request.overview() != null) {
            company.setOverview(request.overview());
        }
        return CompanyDetail.from(company, questionService.listByCompany(companyId));
    }

    public void delete(UUID companyId) {
        Company company = findOrThrow(companyId);
        companyRepository.delete(company);
    }

    @Transactional(readOnly = true)
    public Company findOrThrow(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("会社が見つかりません: " + companyId));
    }
}
