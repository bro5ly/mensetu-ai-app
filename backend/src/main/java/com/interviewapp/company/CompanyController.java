package com.interviewapp.company;

import com.interviewapp.company.CompanyDtos.CompanyDetail;
import com.interviewapp.company.CompanyDtos.CompanySummary;
import com.interviewapp.company.CompanyDtos.CreateCompanyRequest;
import com.interviewapp.company.CompanyDtos.UpdateCompanyRequest;
import com.interviewapp.question.QuestionDtos.CreateQuestionRequest;
import com.interviewapp.question.QuestionDtos.QuestionResponse;
import com.interviewapp.question.QuestionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;
    private final QuestionService questionService;

    public CompanyController(CompanyService companyService, QuestionService questionService) {
        this.companyService = companyService;
        this.questionService = questionService;
    }

    @GetMapping
    public List<CompanySummary> list() {
        return companyService.list();
    }

    @GetMapping("/{companyId}")
    public CompanyDetail get(@PathVariable UUID companyId) {
        return companyService.get(companyId);
    }

    @PostMapping
    public ResponseEntity<CompanyDetail> create(@Valid @RequestBody CreateCompanyRequest request) {
        CompanyDetail created = companyService.create(request);
        return ResponseEntity.created(URI.create("/api/companies/" + created.id())).body(created);
    }

    @PatchMapping("/{companyId}")
    public CompanyDetail update(@PathVariable UUID companyId, @Valid @RequestBody UpdateCompanyRequest request) {
        return companyService.update(companyId, request);
    }

    @DeleteMapping("/{companyId}")
    public ResponseEntity<Void> delete(@PathVariable UUID companyId) {
        companyService.delete(companyId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{companyId}/questions")
    public List<QuestionResponse> listQuestions(@PathVariable UUID companyId) {
        companyService.findOrThrow(companyId);
        return questionService.listByCompany(companyId);
    }

    @PostMapping("/{companyId}/questions")
    public ResponseEntity<QuestionResponse> addQuestion(
            @PathVariable UUID companyId, @Valid @RequestBody CreateQuestionRequest request) {
        companyService.findOrThrow(companyId);
        QuestionResponse created = questionService.add(companyId, request);
        return ResponseEntity.created(URI.create("/api/questions/" + created.id())).body(created);
    }
}
