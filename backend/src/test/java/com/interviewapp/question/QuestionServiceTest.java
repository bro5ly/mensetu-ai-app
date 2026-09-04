package com.interviewapp.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewapp.common.NotFoundException;
import com.interviewapp.question.QuestionDtos.CreateQuestionRequest;
import com.interviewapp.question.QuestionDtos.QuestionResponse;
import com.interviewapp.question.QuestionDtos.UpdateQuestionRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

    @Mock
    private InterviewQuestionRepository questionRepository;

    @InjectMocks
    private QuestionService questionService;

    @Test
    void 追加時にdisplayOrder未指定なら既存件数を採番する() {
        UUID companyId = UUID.randomUUID();
        when(questionRepository.countByCompanyId(companyId)).thenReturn(2L);
        when(questionRepository.save(any(InterviewQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

        QuestionResponse response = questionService.add(companyId,
                new CreateQuestionRequest("  自己PRをしてください  ", "SELF_PR", null));

        assertThat(response.displayOrder()).isEqualTo(2);
        assertThat(response.questionText()).isEqualTo("自己PRをしてください");
    }

    @Test
    void 更新は指定されたフィールドだけ反映する() {
        UUID questionId = UUID.randomUUID();
        InterviewQuestion question = new InterviewQuestion(UUID.randomUUID(), "旧テキスト", "SELF_PR", 0);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));

        QuestionResponse response = questionService.update(questionId,
                new UpdateQuestionRequest("新テキスト", null, null));

        assertThat(response.questionText()).isEqualTo("新テキスト");
        assertThat(response.internalCategory()).isEqualTo("SELF_PR");
    }

    @Test
    void 存在しない質問の削除はNotFound() {
        UUID questionId = UUID.randomUUID();
        when(questionRepository.findById(questionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> questionService.delete(questionId))
                .isInstanceOf(NotFoundException.class);
        verify(questionRepository, org.mockito.Mockito.never()).delete(any());
    }
}
