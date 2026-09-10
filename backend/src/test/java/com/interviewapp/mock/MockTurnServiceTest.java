package com.interviewapp.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewapp.common.ConflictException;
import com.interviewapp.company.Company;
import com.interviewapp.company.CompanyRepository;
import com.interviewapp.practice.PracticeCoachLlm;
import com.interviewapp.profile.UserProfileService;
import com.interviewapp.session.ChatMessage;
import com.interviewapp.session.ChatMessageRepository;
import com.interviewapp.session.ChatSession;
import com.interviewapp.session.ChatSessionRepository;
import com.interviewapp.session.EndedReason;
import com.interviewapp.session.MessageRole;
import com.interviewapp.session.MessageType;
import com.interviewapp.session.SessionStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class MockTurnServiceTest {

    @Mock
    private ChatSessionRepository sessionRepository;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private MockSessionQuestionRepository questionRepository;
    @Mock
    private UserProfileService userProfileService;

    private MockTurnService service;
    private final UUID sessionId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private List<String> llmChunks;
    private String lastSystemPrompt;
    private List<PracticeCoachLlm.Turn> lastHistory;
    private int llmCallCount;

    static final class RecordingListener implements MockTurnListener {
        int startCalls;
        final StringBuilder streamed = new StringBuilder();
        String endContent;
        String advancedQuestionText;
        Integer advancedIndex;
        Integer advancedTotal;
        boolean ended;

        @Override
        public void onAssistantStart() {
            startCalls++;
        }

        @Override
        public void onAssistantChunk(String textChunk) {
            streamed.append(textChunk);
        }

        @Override
        public void onAssistantEnd(String fullContent) {
            endContent = fullContent;
        }

        @Override
        public void onQuestionAdvance(String nextQuestionText, int questionIndex, int totalQuestions) {
            advancedQuestionText = nextQuestionText;
            advancedIndex = questionIndex;
            advancedTotal = totalQuestions;
        }

        @Override
        public void onInterviewEnd() {
            ended = true;
        }
    }

    @BeforeEach
    void setUp() {
        PracticeCoachLlm llm = (systemPrompt, history, userMessage) -> {
            lastSystemPrompt = systemPrompt;
            lastHistory = history;
            llmCallCount++;
            return Flux.fromIterable(llmChunks);
        };
        service = new MockTurnService(sessionRepository, messageRepository, companyRepository,
                questionRepository, new MockInterviewPromptFactory(), new MockTurnMarkerParser(), llm,
                userProfileService);

        lenient().when(companyRepository.findById(companyId))
                .thenReturn(Optional.of(new Company("ABC商事", "・挑戦を後押しする文化がある")));
        lenient().when(messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId))
                .thenReturn(new ArrayList<>());
        lenient().when(userProfileService.getResumeTextOrNull()).thenReturn(null);
    }

    private ChatSession mockSession() {
        return ChatSession.newMock(companyId, UUID.randomUUID());
    }

    private List<MockSessionQuestion> flow(int size) {
        List<MockSessionQuestion> questions = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            questions.add(new MockSessionQuestion(sessionId, "質問" + (i + 1) + "です", "CATEGORY", i));
        }
        return questions;
    }

    @Test
    void 面接開始でREADYからIN_PROGRESSになり最初の質問がLLM呼び出しなしでそのまま発話される() {
        ChatSession session = mockSession();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(flow(5));
        RecordingListener listener = new RecordingListener();

        service.startInterview(sessionId, listener);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(session.getStartedAt()).isNotNull();
        assertThat(session.getCurrentQuestionOrder()).isZero();
        assertThat(listener.startCalls).isEqualTo(1);
        assertThat(listener.endContent).isEqualTo("質問1です");
        assertThat(listener.ended).isFalse();
        assertThat(listener.advancedQuestionText).isNull();
        assertThat(llmCallCount).isZero();

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(saved.getValue().getContent()).isEqualTo("質問1です");
        assertThat(saved.getValue().getSequenceNo()).isZero();
        assertThat(saved.getValue().getQuestionOrder()).isZero();
    }

    @Test
    void 既に開始済みのセッションでは何もしない() {
        ChatSession session = mockSession();
        session.setStatus(SessionStatus.IN_PROGRESS);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        service.startInterview(sessionId, new RecordingListener());

        verify(messageRepository, never()).save(any());
    }

    @Test
    void 通常応答では質問は進まずフォローアップ回数が増える() {
        ChatSession session = mockSession();
        session.setStatus(SessionStatus.IN_PROGRESS);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(flow(5));
        llmChunks = List.of("それは具体的にどういうことですか？");
        RecordingListener listener = new RecordingListener();

        service.handleUserTurn(sessionId, "サークル活動を頑張りました", listener);

        assertThat(session.getCurrentQuestionOrder()).isZero();
        assertThat(session.getCurrentQuestionFollowups()).isEqualTo(1);
        assertThat(listener.advancedQuestionText).isNull();
        assertThat(listener.ended).isFalse();
        assertThat(listener.endContent).isEqualTo("それは具体的にどういうことですか？");

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, times(2)).save(saved.capture());
        ChatMessage userMessage = saved.getAllValues().get(0);
        ChatMessage assistantMessage = saved.getAllValues().get(1);
        assertThat(userMessage.getRole()).isEqualTo(MessageRole.USER);
        assertThat(userMessage.getQuestionOrder()).isZero();
        assertThat(assistantMessage.getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(assistantMessage.getContent()).isEqualTo("それは具体的にどういうことですか？");
        assertThat(assistantMessage.getQuestionOrder()).isZero();
    }

    @Test
    void LLMに渡す履歴は今の質問に属するやり取りだけに絞られる() {
        ChatSession session = mockSession();
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setCurrentQuestionOrder(1);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(flow(5));

        ChatMessage selfIntroQuestion =
                new ChatMessage(sessionId, MessageRole.ASSISTANT, "簡単に自己紹介をお願いします。", MessageType.NORMAL, 0);
        selfIntroQuestion.setQuestionOrder(0);
        ChatMessage selfIntroAnswer =
                new ChatMessage(sessionId, MessageRole.USER, "AtCoderで学習しています。", MessageType.NORMAL, 1);
        selfIntroAnswer.setQuestionOrder(0);
        ChatMessage q2Question =
                new ChatMessage(sessionId, MessageRole.ASSISTANT, "質問2です", MessageType.NORMAL, 2);
        q2Question.setQuestionOrder(1);
        ChatMessage q2Answer =
                new ChatMessage(sessionId, MessageRole.USER, "サークル活動を頑張りました。", MessageType.NORMAL, 3);
        q2Answer.setQuestionOrder(1);
        when(messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId))
                .thenReturn(new ArrayList<>(List.of(selfIntroQuestion, selfIntroAnswer, q2Question, q2Answer)));

        llmChunks = List.of("それは具体的にどういうことですか？");
        RecordingListener listener = new RecordingListener();

        service.handleUserTurn(sessionId, "それは大変でした", listener);

        // 自己紹介(questionOrder=0)のやり取りは含まれず、今の質問(questionOrder=1)の
        // やり取りだけがLLMへの履歴として渡される。
        assertThat(lastHistory).hasSize(2);
        assertThat(lastHistory.get(0).content()).isEqualTo("質問2です");
        assertThat(lastHistory.get(1).content()).isEqualTo("サークル活動を頑張りました。");
    }

    @Test
    void QUESTION_DONEマーカーで次の質問がLLM呼び出しなしでそのまま発話されフォローアップ回数がリセットされる() {
        ChatSession session = mockSession();
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setCurrentQuestionFollowups(2);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(flow(5));
        llmChunks = List.of("<<QUESTION_DONE>>");
        RecordingListener listener = new RecordingListener();

        service.handleUserTurn(sessionId, "学生時代はサークル活動をしていました", listener);

        assertThat(session.getCurrentQuestionOrder()).isEqualTo(1);
        assertThat(session.getCurrentQuestionFollowups()).isZero();
        assertThat(listener.advancedQuestionText).isEqualTo("質問2です");
        assertThat(listener.advancedIndex).isEqualTo(2);
        assertThat(listener.advancedTotal).isEqualTo(5);
        // マーカー検出時、モデルの反応文は使わず次の質問の文面だけがそのまま話される
        // (次の質問の提示はコード側で確定的に行う。詳細はMockTurnService参照)。
        assertThat(listener.endContent).isEqualTo("質問2です");

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getContent()).isEqualTo("質問2です");
        assertThat(saved.getAllValues().get(1).getQuestionOrder()).isEqualTo(1);
    }

    @Test
    void フォローアップ上限に達するとLLMを呼ばずに次の質問へ進む() {
        ChatSession session = mockSession();
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setCurrentQuestionOrder(1);
        session.setCurrentQuestionFollowups(MockTurnService.MAX_FOLLOWUPS_PER_QUESTION);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(flow(5));
        RecordingListener listener = new RecordingListener();

        service.handleUserTurn(sessionId, "特にありません", listener);

        assertThat(session.getCurrentQuestionOrder()).isEqualTo(2);
        assertThat(session.getCurrentQuestionFollowups()).isZero();
        assertThat(listener.advancedQuestionText).isEqualTo("質問3です");
        assertThat(listener.endContent).isEqualTo("質問3です");
        assertThat(llmCallCount).isZero();
    }

    @Test
    void 最後の質問でQUESTION_DONEになると締めくくりの固定文言とともに面接が終了する() {
        ChatSession session = mockSession();
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setCurrentQuestionOrder(4);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(flow(5));
        llmChunks = List.of("<<QUESTION_DONE>>");
        RecordingListener listener = new RecordingListener();

        service.handleUserTurn(sessionId, "以上です", listener);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ENDED);
        assertThat(session.getEndedReason()).isEqualTo(EndedReason.AI_JUDGED);
        assertThat(session.getEndedAt()).isNotNull();
        assertThat(listener.ended).isTrue();
        assertThat(listener.advancedQuestionText).isNull();
        assertThat(listener.endContent).isEqualTo(MockInterviewPromptFactory.INTERVIEW_CLOSING_MESSAGE);

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getContent())
                .isEqualTo(MockInterviewPromptFactory.INTERVIEW_CLOSING_MESSAGE);
    }

    @Test
    void 最後の質問でフォローアップ上限に達しても面接が終了する() {
        ChatSession session = mockSession();
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setCurrentQuestionOrder(4);
        session.setCurrentQuestionFollowups(MockTurnService.MAX_FOLLOWUPS_PER_QUESTION);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(flow(5));
        RecordingListener listener = new RecordingListener();

        service.handleUserTurn(sessionId, "以上です", listener);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ENDED);
        assertThat(listener.ended).isTrue();
        assertThat(llmCallCount).isZero();
    }

    @Test
    void 終了済みセッションへの発言はConflict() {
        ChatSession session = mockSession();
        session.setStatus(SessionStatus.ENDED);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.handleUserTurn(sessionId, "まだ話したいです", new RecordingListener()))
                .isInstanceOf(ConflictException.class);
        verify(messageRepository, never()).save(any());
    }
}
