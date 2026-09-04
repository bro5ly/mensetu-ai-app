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
                new CompanyDetail(id, "新会社", null, Instant.now(), Instant.now(), List.of()));

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
}
