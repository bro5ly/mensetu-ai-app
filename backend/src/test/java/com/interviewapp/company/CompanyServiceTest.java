package com.interviewapp.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.interviewapp.company.CompanyDtos.CompanyDetail;
import com.interviewapp.company.CompanyDtos.CompanySummary;
import com.interviewapp.company.CompanyDtos.CreateCompanyRequest;
import com.interviewapp.company.CompanySourceDtos.FetchedSourcePreview;
import com.interviewapp.company.CompanySourceDtos.SourceResponse;
import com.interviewapp.question.QuestionDtos.CreateQuestionRequest;
import com.interviewapp.question.QuestionDtos.QuestionResponse;
import com.interviewapp.question.QuestionService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private QuestionService questionService;

    @Mock
    private CompanySourceService companySourceService;

    @InjectMocks
    private CompanyService companyService;

    @Test
    void 一覧は汎用的な質問の会社を含めない() {
        Company normal = new Company("ABC商事", null);
        normal.setId(UUID.randomUUID());
        when(companyRepository.findByGenericFalseOrderByCreatedAtAsc()).thenReturn(List.of(normal));

        List<CompanySummary> summaries = companyService.list();

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).id()).isEqualTo(normal.getId());
    }

    @Test
    void 汎用的な質問の詳細を取得できる() {
        Company generic = new Company("汎用的な質問", null);
        generic.setId(UUID.randomUUID());
        generic.setGeneric(true);
        when(companyRepository.findByGenericTrue()).thenReturn(Optional.of(generic));
        when(questionService.listByCompany(generic.getId())).thenReturn(List.of(
                new QuestionResponse(UUID.randomUUID(), generic.getId(), "自己PRをしてください", "SELF_PR", 0, Instant.now())));
        when(companySourceService.listSources(generic.getId())).thenReturn(List.of());

        CompanyDetail detail = companyService.getGeneric();

        assertThat(detail.id()).isEqualTo(generic.getId());
        assertThat(detail.questions()).hasSize(1);
    }

    @Test
    void 汎用的な質問の会社が無ければ取得に失敗する() {
        when(companyRepository.findByGenericTrue()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.getGeneric()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 汎用的な質問の会社は削除できない() {
        Company generic = new Company("汎用的な質問", null);
        UUID genericId = UUID.randomUUID();
        generic.setId(genericId);
        generic.setGeneric(true);
        when(companyRepository.findById(genericId)).thenReturn(Optional.of(generic));

        assertThatThrownBy(() -> companyService.delete(genericId)).isInstanceOf(IllegalArgumentException.class);
        verify(companyRepository, never()).delete(any());
    }

    @Test
    void 質問付きで会社を作成すると質問も順番に登録される() {
        Company saved = new Company("ABC商事", "概要");
        saved.setId(UUID.randomUUID());
        when(companyRepository.saveAndFlush(any())).thenReturn(saved);
        when(questionService.add(any(), any())).thenAnswer(inv -> {
            CreateQuestionRequest req = inv.getArgument(1);
            return new QuestionResponse(UUID.randomUUID(), saved.getId(), req.questionText(),
                    null, req.displayOrder(), Instant.now());
        });

        CompanyDetail detail = companyService.create(
                new CreateCompanyRequest("ABC商事", "概要", List.of("Q1", "Q2", "Q3"), null));

        assertThat(detail.questions()).hasSize(3);
        ArgumentCaptor<CreateQuestionRequest> captor = ArgumentCaptor.forClass(CreateQuestionRequest.class);
        verify(questionService, times(3)).add(eq(saved.getId()), captor.capture());
        assertThat(captor.getAllValues())
                .extracting(CreateQuestionRequest::displayOrder)
                .containsExactly(0, 1, 2);
        assertThat(captor.getAllValues())
                .extracting(CreateQuestionRequest::questionText)
                .containsExactly("Q1", "Q2", "Q3");
    }

    @Test
    void questionsが空白のみの要素はスキップする() {
        Company saved = new Company("ABC商事", null);
        saved.setId(UUID.randomUUID());
        when(companyRepository.saveAndFlush(any())).thenReturn(saved);
        when(questionService.add(any(), any())).thenAnswer(inv -> {
            CreateQuestionRequest req = inv.getArgument(1);
            return new QuestionResponse(UUID.randomUUID(), saved.getId(), req.questionText(),
                    null, req.displayOrder(), Instant.now());
        });

        CompanyDetail detail = companyService.create(
                new CreateCompanyRequest("ABC商事", null, List.of("有効な質問1", "  ", "有効な質問2"), null));

        assertThat(detail.questions()).hasSize(2);
        verify(questionService, times(2)).add(any(), any());
    }

    @Test
    void questionsなしなら質問は作られない() {
        Company saved = new Company("ABC商事", null);
        saved.setId(UUID.randomUUID());
        when(companyRepository.saveAndFlush(any())).thenReturn(saved);

        CompanyDetail detail = companyService.create(new CreateCompanyRequest("ABC商事", null, null, null));

        assertThat(detail.questions()).isEmpty();
        verifyNoInteractions(questionService);
    }

    @Test
    void sources付きで会社を作成するとソースも登録される() {
        Company saved = new Company("ABC商事", "概要");
        saved.setId(UUID.randomUUID());
        when(companyRepository.saveAndFlush(any())).thenReturn(saved);
        when(companySourceService.persistFetched(any(), any())).thenAnswer(inv -> {
            FetchedSourcePreview preview = inv.getArgument(1);
            return new SourceResponse(UUID.randomUUID(), saved.getId(), preview.url(), preview.title(),
                    preview.content(), Instant.now(), Instant.now());
        });

        CompanyDetail detail = companyService.create(new CreateCompanyRequest("ABC商事", "概要", null, List.of(
                new FetchedSourcePreview("https://example.com", "採用ページ", "本文テキスト"))));

        assertThat(detail.sources()).hasSize(1);
        ArgumentCaptor<FetchedSourcePreview> captor = ArgumentCaptor.forClass(FetchedSourcePreview.class);
        verify(companySourceService, times(1)).persistFetched(eq(saved.getId()), captor.capture());
        assertThat(captor.getValue().url()).isEqualTo("https://example.com");
    }

    @Test
    void sourcesなしならソースは作られない() {
        Company saved = new Company("ABC商事", null);
        saved.setId(UUID.randomUUID());
        when(companyRepository.saveAndFlush(any())).thenReturn(saved);

        CompanyDetail detail = companyService.create(new CreateCompanyRequest("ABC商事", null, null, null));

        assertThat(detail.sources()).isEmpty();
        verifyNoInteractions(companySourceService);
    }
}
