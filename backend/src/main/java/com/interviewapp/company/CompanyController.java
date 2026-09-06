package com.interviewapp.company;

import com.interviewapp.company.CompanyDtos.CompanyDetail;
import com.interviewapp.company.CompanyDtos.CompanySummary;
import com.interviewapp.company.CompanyDtos.CreateCompanyRequest;
import com.interviewapp.company.CompanyDtos.GenerateQuestionsRequest;
import com.interviewapp.company.CompanyDtos.GeneratedQuestions;
import com.interviewapp.company.CompanyDtos.ResearchCompanyRequest;
import com.interviewapp.company.CompanyDtos.ResearchDraft;
import com.interviewapp.company.CompanyDtos.UpdateCompanyRequest;
import com.interviewapp.company.CompanySourceDtos.BatchAddSourcesRequest;
import com.interviewapp.company.CompanySourceDtos.BatchAddSourcesResponse;
import com.interviewapp.company.CompanySourceDtos.CreateSourceRequest;
import com.interviewapp.company.CompanySourceDtos.FetchSourceRequest;
import com.interviewapp.company.CompanySourceDtos.FetchedSourcePreview;
import com.interviewapp.company.CompanySourceDtos.SourceResponse;
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
    private final CompanyResearchService companyResearchService;
    private final CompanySourceService companySourceService;

    public CompanyController(
            CompanyService companyService,
            QuestionService questionService,
            CompanyResearchService companyResearchService,
            CompanySourceService companySourceService) {
        this.companyService = companyService;
        this.questionService = questionService;
        this.companyResearchService = companyResearchService;
        this.companySourceService = companySourceService;
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

    /** 企業リサーチ: 会社名＋登録済みソースから企業概要の下書きを生成する(未保存)。 */
    @PostMapping("/research")
    public ResearchDraft research(@Valid @RequestBody ResearchCompanyRequest request) {
        return companyResearchService.research(
                request.name(), request.sources(), request.currentOverview(), request.feedback());
    }

    /** 企業リサーチ: 確認済みの企業概要から面接想定質問を生成する(未保存)。 */
    @PostMapping("/generate-questions")
    public GeneratedQuestions generateQuestions(@Valid @RequestBody GenerateQuestionsRequest request) {
        return companyResearchService.generateQuestions(request.name(), request.overview());
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

    /** 会社追加ウィザードのソース添付ステップ: URLをfetchするだけで保存しない。 */
    @PostMapping("/sources/fetch")
    public FetchedSourcePreview fetchSourcePreview(@Valid @RequestBody FetchSourceRequest request) {
        return companySourceService.previewFetch(request.url());
    }

    @GetMapping("/{companyId}/sources")
    public List<SourceResponse> listSources(@PathVariable UUID companyId) {
        companyService.findOrThrow(companyId);
        return companySourceService.listSources(companyId);
    }

    @PostMapping("/{companyId}/sources")
    public ResponseEntity<SourceResponse> addSource(
            @PathVariable UUID companyId, @Valid @RequestBody CreateSourceRequest request) {
        companyService.findOrThrow(companyId);
        SourceResponse created = companySourceService.addSource(companyId, request.url());
        return ResponseEntity.created(URI.create("/api/companies/" + companyId + "/sources/" + created.id()))
                .body(created);
    }

    /** 既存の会社にURLをまとめて追加する。1件の失敗が他をブロックしないよう、成功/失敗を分けて返す。 */
    @PostMapping("/{companyId}/sources/batch")
    public BatchAddSourcesResponse addSources(
            @PathVariable UUID companyId, @Valid @RequestBody BatchAddSourcesRequest request) {
        companyService.findOrThrow(companyId);
        return companySourceService.addSources(companyId, request.urls());
    }
}
