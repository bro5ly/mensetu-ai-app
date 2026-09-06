package com.interviewapp.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionBankServiceTest {

    @Mock
    private QuestionBankRepository repository;

    @InjectMocks
    private QuestionBankService service;

    @Test
    void カテゴリごとに最大2件ずつバランスよくサンプルする() {
        when(repository.findByInternalCategoryOrderByCreatedAtAsc("SELF_PR")).thenReturn(List.of(
                new QuestionBankEntry("SELF_PR", "自己PR1", "型1"),
                new QuestionBankEntry("SELF_PR", "自己PR2", "型2"),
                new QuestionBankEntry("SELF_PR", "自己PR3", "型3")));
        when(repository.findByInternalCategoryOrderByCreatedAtAsc("MOTIVATION")).thenReturn(List.of(
                new QuestionBankEntry("MOTIVATION", "志望動機1", "型4")));
        when(repository.findByInternalCategoryOrderByCreatedAtAsc("EXPERIENCE")).thenReturn(List.of());
        when(repository.findByInternalCategoryOrderByCreatedAtAsc("REVERSE_QUESTION")).thenReturn(List.of());

        List<QuestionBankEntry> sample = service.sampleForPrompt();

        assertThat(sample).extracting(QuestionBankEntry::getQuestionText)
                .containsExactly("自己PR1", "自己PR2", "志望動機1");
    }

    @Test
    void 全カテゴリ空なら空リストを返す() {
        when(repository.findByInternalCategoryOrderByCreatedAtAsc(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of());

        assertThat(service.sampleForPrompt()).isEmpty();
    }
}
