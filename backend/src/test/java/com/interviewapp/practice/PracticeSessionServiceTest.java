package com.interviewapp.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewapp.common.NotFoundException;
import com.interviewapp.question.InterviewQuestion;
import com.interviewapp.question.QuestionService;
import com.interviewapp.session.ChatSession;
import com.interviewapp.session.ChatSessionRepository;
import com.interviewapp.session.SessionDtos.PracticeSessionResponse;
import com.interviewapp.session.SessionMode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PracticeSessionServiceTest {

    @Mock
    private QuestionService questionService;

    @Mock
    private ChatSessionRepository sessionRepository;

    @InjectMocks
    private PracticeSessionService practiceSessionService;

    private InterviewQuestion question(UUID companyId) {
        return new InterviewQuestion(companyId, "志望動機を教えてください", "MOTIVATION", 0);
    }

    @Test
    void 既存の練習セッションがなければ新規作成する() {
        UUID questionId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(questionService.findOrThrow(questionId)).thenReturn(question(companyId));
        when(sessionRepository.findByQuestionIdAndMode(questionId, SessionMode.PRACTICE))
                .thenReturn(Optional.empty());
        when(sessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        PracticeSessionResponse response = practiceSessionService.startOrResume(questionId);

        assertThat(response.resumed()).isFalse();
        assertThat(response.companyId()).isEqualTo(companyId);
        assertThat(response.questionId()).isEqualTo(questionId);
        verify(sessionRepository).save(any(ChatSession.class));
    }

    @Test
    void 既存の練習セッションがあれば再開して返す() {
        UUID questionId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        ChatSession existing = ChatSession.newPractice(companyId, questionId);
        when(questionService.findOrThrow(questionId)).thenReturn(question(companyId));
        when(sessionRepository.findByQuestionIdAndMode(questionId, SessionMode.PRACTICE))
                .thenReturn(Optional.of(existing));

        PracticeSessionResponse response = practiceSessionService.startOrResume(questionId);

        assertThat(response.resumed()).isTrue();
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void 存在しない質問はNotFound() {
        UUID questionId = UUID.randomUUID();
        when(questionService.findOrThrow(questionId)).thenThrow(new NotFoundException("なし"));

        assertThatThrownBy(() -> practiceSessionService.startOrResume(questionId))
                .isInstanceOf(NotFoundException.class);
    }
}
