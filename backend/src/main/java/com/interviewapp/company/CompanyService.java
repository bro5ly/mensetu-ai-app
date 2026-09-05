package com.interviewapp.company;

import com.interviewapp.common.NotFoundException;
import com.interviewapp.company.CompanyDtos.CompanyDetail;
import com.interviewapp.company.CompanyDtos.CompanySummary;
import com.interviewapp.company.CompanyDtos.CreateCompanyRequest;
import com.interviewapp.company.CompanyDtos.UpdateCompanyRequest;
import com.interviewapp.company.CompanySourceDtos.FetchedSourcePreview;
import com.interviewapp.company.CompanySourceDtos.SourceResponse;
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
    private final CompanySourceService companySourceService;

    public CompanyService(
            CompanyRepository companyRepository,
            QuestionService questionService,
            CompanySourceService companySourceService) {
        this.companyRepository = companyRepository;
        this.questionService = questionService;
        this.companySourceService = companySourceService;
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
        return CompanyDetail.from(company, questionService.listByCompany(companyId),
                companySourceService.listSources(companyId));
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

        List<SourceResponse> sources = List.of();
        if (request.sources() != null && !request.sources().isEmpty()) {
            List<SourceResponse> created = new ArrayList<>();
            for (FetchedSourcePreview preview : request.sources()) {
                if (preview != null && StringUtils.hasText(preview.url())) {
                    created.add(companySourceService.persistFetched(company.getId(), preview));
                }
            }
            sources = created;
        }
        return CompanyDetail.from(company, questions, sources);
    }

    public CompanyDetail update(UUID companyId, UpdateCompanyRequest request) {
        Company company = findOrThrow(companyId);
        if (StringUtils.hasText(request.name())) {
            company.setName(request.name().trim());
        }
        if (request.overview() != null) {
            company.setOverview(request.overview());
        }
        return CompanyDetail.from(company, questionService.listByCompany(companyId),
                companySourceService.listSources(companyId));
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
