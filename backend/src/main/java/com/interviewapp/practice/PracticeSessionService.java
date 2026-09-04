package com.interviewapp.practice;

import com.interviewapp.question.InterviewQuestion;
import com.interviewapp.question.QuestionService;
import com.interviewapp.session.ChatSession;
import com.interviewapp.session.ChatSessionRepository;
import com.interviewapp.session.SessionDtos.PracticeSessionResponse;
import com.interviewapp.session.SessionMode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PracticeSessionService {

    private final QuestionService questionService;
    private final ChatSessionRepository sessionRepository;

    public PracticeSessionService(QuestionService questionService, ChatSessionRepository sessionRepository) {
        this.questionService = questionService;
        this.sessionRepository = sessionRepository;
    }

    /**
     * 質問に対する練習セッションを開始する。
     * 練習セッションは質問ごとに1つを使い回すため、既存があればそれを再開して返す。
     */
    public PracticeSessionResponse startOrResume(UUID questionId) {
        InterviewQuestion question = questionService.findOrThrow(questionId);

        return sessionRepository.findByQuestionIdAndMode(questionId, SessionMode.PRACTICE)
                .map(existing -> PracticeSessionResponse.from(existing, true))
                .orElseGet(() -> {
                    ChatSession created = sessionRepository.save(
                            ChatSession.newPractice(question.getCompanyId(), questionId));
                    return PracticeSessionResponse.from(created, false);
                });
    }
}
