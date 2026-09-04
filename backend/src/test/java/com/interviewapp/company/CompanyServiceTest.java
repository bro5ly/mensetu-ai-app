package com.interviewapp.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.interviewapp.company.CompanyDtos.CompanyDetail;
import com.interviewapp.company.CompanyDtos.CreateCompanyRequest;
import com.interviewapp.question.QuestionDtos.CreateQuestionRequest;
import com.interviewapp.question.QuestionDtos.QuestionResponse;
import com.interviewapp.question.QuestionService;
import java.time.Instant;
import java.util.List;
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

    @InjectMocks
    private CompanyService companyService;

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
                new CreateCompanyRequest("ABC商事", "概要", List.of("Q1", "Q2", "Q3")));

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
                new CreateCompanyRequest("ABC商事", null, List.of("有効な質問1", "  ", "有効な質問2")));

        assertThat(detail.questions()).hasSize(2);
        verify(questionService, times(2)).add(any(), any());
    }

    @Test
    void questionsなしなら質問は作られない() {
        Company saved = new Company("ABC商事", null);
        saved.setId(UUID.randomUUID());
        when(companyRepository.saveAndFlush(any())).thenReturn(saved);

        CompanyDetail detail = companyService.create(new CreateCompanyRequest("ABC商事", null, null));

        assertThat(detail.questions()).isEmpty();
        verifyNoInteractions(questionService);
    }
}
