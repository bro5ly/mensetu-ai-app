package com.interviewapp.company;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.interviewapp.common.NotFoundException;
import com.interviewapp.company.CompanyDtos.CompanyDetail;
import com.interviewapp.company.CompanyDtos.CompanySummary;
import com.interviewapp.company.CompanyDtos.GeneratedQuestions;
import com.interviewapp.company.CompanyDtos.ResearchDraft;
import com.interviewapp.company.CompanySourceDtos.FetchedSourcePreview;
import com.interviewapp.company.CompanySourceDtos.SourceResponse;
import com.interviewapp.question.QuestionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CompanyController.class)
class CompanyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompanyService companyService;

    @MockitoBean
    private QuestionService questionService;

    @MockitoBean
    private CompanyResearchService companyResearchService;

    @MockitoBean
    private CompanySourceService companySourceService;

    @Test
    void 会社一覧を返す() throws Exception {
        when(companyService.list()).thenReturn(List.of(
                new CompanySummary(UUID.randomUUID(), "ABCコーポレーション", Instant.now())));

        mockMvc.perform(get("/api/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("ABCコーポレーション"));
    }

    @Test
    void 存在しない会社詳細は404() throws Exception {
        when(companyService.get(any())).thenThrow(new NotFoundException("会社が見つかりません"));

        mockMvc.perform(get("/api/companies/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void 会社作成は201とLocationを返す() throws Exception {
        UUID id = UUID.randomUUID();
        when(companyService.create(any())).thenReturn(
                new CompanyDetail(id, "新会社", null, Instant.now(), Instant.now(), List.of(), List.of()));

        mockMvc.perform(post("/api/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新会社\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void 会社名が空なら400() throws Exception {
        mockMvc.perform(post("/api/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 企業リサーチは概要下書きを返す() throws Exception {
        when(companyResearchService.research(any(), any(), any(), any()))
                .thenReturn(new ResearchDraft("・挑戦を後押しする文化\n面接では具体性が見られる。"));

        mockMvc.perform(post("/api/companies/research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ABC商事\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview").value("・挑戦を後押しする文化\n面接では具体性が見られる。"));
    }

    @Test
    void 企業リサーチは会社名が空なら400() throws Exception {
        mockMvc.perform(post("/api/companies/research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 質問生成は3件の配列を返す() throws Exception {
        when(companyResearchService.generateQuestions(any(), any()))
                .thenReturn(new GeneratedQuestions(List.of("志望動機は？", "強みは？", "逆質問は？")));

        mockMvc.perform(post("/api/companies/generate-questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ABC商事\",\"overview\":\"概要\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(3))
                .andExpect(jsonPath("$.questions[0]").value("志望動機は？"));
    }

    @Test
    void 質問生成はoverviewが空なら400() throws Exception {
        mockMvc.perform(post("/api/companies/generate-questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ABC商事\",\"overview\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ソースfetchプレビューはタイトルと本文を返す() throws Exception {
        when(companySourceService.previewFetch(any()))
                .thenReturn(new FetchedSourcePreview("https://example.com", "採用ページ", "本文テキスト"));

        mockMvc.perform(post("/api/companies/sources/fetch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("採用ページ"))
                .andExpect(jsonPath("$.content").value("本文テキスト"));
    }

    @Test
    void ソース追加は201とLocationを返す() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        when(companySourceService.addSource(any(), any())).thenReturn(new SourceResponse(
                sourceId, companyId, "https://example.com", "採用ページ", "本文テキスト", Instant.now(), Instant.now()));

        mockMvc.perform(post("/api/companies/{companyId}/sources", companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(sourceId.toString()));
    }
}
