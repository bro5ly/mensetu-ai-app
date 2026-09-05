package com.interviewapp.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewapp.common.ConflictException;
import com.interviewapp.company.Company;
import com.interviewapp.company.CompanyRepository;
import com.interviewapp.company.CompanySourceService;
import com.interviewapp.question.InterviewQuestion;
import com.interviewapp.question.InterviewQuestionRepository;
import com.interviewapp.session.ChatMessage;
import com.interviewapp.session.ChatMessageRepository;
import com.interviewapp.session.ChatSession;
import com.interviewapp.session.ChatSessionRepository;
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
class PracticeTurnServiceTest {

    @Mock
    private ChatSessionRepository sessionRepository;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private InterviewQuestionRepository questionRepository;
    @Mock
    private CompanySourceService companySourceService;

    private PracticeTurnService service;
    private final UUID sessionId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();
    private List<String> llmChunks;

    static final class RecordingListener implements PracticeTurnListener {
        MessageType startType;
        final StringBuilder streamed = new StringBuilder();
        MessageType endType;
        String endContent;
        int startCalls;

        @Override
        public void onAssistantStart(MessageType messageType) {
            startType = messageType;
            startCalls++;
        }

        @Override
        public void onAssistantChunk(String textChunk) {
            streamed.append(textChunk);
        }

        @Override
        public void onAssistantEnd(MessageType messageType, String fullContent) {
            endType = messageType;
            endContent = fullContent;
        }
    }

    @BeforeEach
    void setUp() {
        PracticeCoachLlm llm = (systemPrompt, history, userMessage) -> Flux.fromIterable(llmChunks);
        service = new PracticeTurnService(sessionRepository, messageRepository, companyRepository,
                questionRepository, companySourceService, new PracticePromptFactory(), llm,
                new AssistantMarkerParser());

        lenient().when(companyRepository.findById(companyId)).thenReturn(Optional.of(new Company("ABC社", null)));
        lenient().when(questionRepository.findById(questionId))
                .thenReturn(Optional.of(new InterviewQuestion(companyId, "志望動機は？", "MOTIVATION", 0)));
        lenient().when(messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId))
                .thenReturn(new ArrayList<>());
        lenient().when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(companySourceService.findRelevant(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
    }

    private ChatSession readySession() {
        ChatSession s = ChatSession.newPractice(companyId, questionId);
        return s;
    }

    @Test
    void 通常応答ではNORMALで保存されREADYからIN_PROGRESSになる() {
        ChatSession session = readySession();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        llmChunks = List.of("その", "経験", "から何を学びましたか？");
        RecordingListener listener = new RecordingListener();

        service.handleUserTurn(sessionId, "IT業界に興味があります", listener);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(session.getStartedAt()).isNotNull();
        assertThat(listener.startType).isEqualTo(MessageType.NORMAL);
        assertThat(listener.endContent).isEqualTo("その経験から何を学びましたか？");

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        ChatMessage user = saved.getAllValues().get(0);
        ChatMessage assistant = saved.getAllValues().get(1);
        assertThat(user.getRole()).isEqualTo(MessageRole.USER);
        assertThat(user.getSequenceNo()).isEqualTo(0);
        assertThat(assistant.getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(assistant.getMessageType()).isEqualTo(MessageType.NORMAL);
        assertThat(assistant.getSequenceNo()).isEqualTo(1);
        assertThat(assistant.getContent()).isEqualTo("その経験から何を学びましたか？");
    }

    @Test
    void 冒頭にADVICEマーカーがある応答はADVICEで保存されマーカーが除去される() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(readySession()));
        llmChunks = List.of("<<AD", "VICE>>", "結論から", "話しましょう");
        RecordingListener listener = new RecordingListener();

        service.handleUserTurn(sessionId, "うまく言えません", listener);

        assertThat(listener.startType).isEqualTo(MessageType.ADVICE);
        assertThat(listener.startCalls).isEqualTo(1);
        assertThat(listener.streamed.toString()).isEqualTo("結論から話しましょう");
        assertThat(listener.endContent).isEqualTo("結論から話しましょう");

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).getMessageType()).isEqualTo(MessageType.ADVICE);
        assertThat(saved.getAllValues().get(1).getContent()).isEqualTo("結論から話しましょう");
    }

    @Test
    void 既存メッセージがある場合はシーケンス番号を継続する() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(readySession()));
        List<ChatMessage> existing = new ArrayList<>(List.of(
                new ChatMessage(sessionId, MessageRole.ASSISTANT, "答えてみてください", MessageType.NORMAL, 0),
                new ChatMessage(sessionId, MessageRole.USER, "はい", MessageType.NORMAL, 1),
                new ChatMessage(sessionId, MessageRole.ASSISTANT, "続けて", MessageType.NORMAL, 2)));
        when(messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId)).thenReturn(existing);
        llmChunks = List.of("なるほど");

        service.handleUserTurn(sessionId, "私の強みは継続力です", new RecordingListener());

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getSequenceNo()).isEqualTo(3);
        assertThat(saved.getAllValues().get(1).getSequenceNo()).isEqualTo(4);
    }

    @Test
    void 会社概要と関連ソースがシステムプロンプトに含まれる() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(readySession()));
        Company companyWithOverview = new Company("ABC社", "・挑戦を後押しする文化がある");
        companyWithOverview.setId(companyId);
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(companyWithOverview));
        when(companySourceService.findRelevant(companyId, "志望動機は？", 3))
                .thenReturn(List.of(new com.interviewapp.company.CompanySource(
                        companyId, "https://example.com", "採用ページ", "新卒採用に力を入れている")));

        List<String> capturedPrompt = new ArrayList<>();
        PracticeCoachLlm recordingLlm = (systemPrompt, history, userMessage) -> {
            capturedPrompt.add(systemPrompt);
            return Flux.fromIterable(List.of("なるほど"));
        };
        PracticeTurnService recordingService = new PracticeTurnService(sessionRepository, messageRepository,
                companyRepository, questionRepository, companySourceService, new PracticePromptFactory(),
                recordingLlm, new AssistantMarkerParser());

        recordingService.handleUserTurn(sessionId, "私の強みは継続力です", new RecordingListener());

        assertThat(capturedPrompt).hasSize(1);
        assertThat(capturedPrompt.get(0)).contains("・挑戦を後押しする文化がある");
        assertThat(capturedPrompt.get(0)).contains("新卒採用に力を入れている");
    }

    @Test
    void LLMが失敗するとPracticeCoachExceptionが伝播しアシスタント発話は保存されない() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(readySession()));
        PracticeCoachLlm failing = (systemPrompt, history, userMessage) ->
                Flux.error(new PracticeCoachException("AI サーバー(Ollama)に接続できませんでした。", null));
        PracticeTurnService failingService = new PracticeTurnService(sessionRepository, messageRepository,
                companyRepository, questionRepository, companySourceService, new PracticePromptFactory(), failing,
                new AssistantMarkerParser());

        assertThatThrownBy(() ->
                failingService.handleUserTurn(sessionId, "IT業界に興味があります", new RecordingListener()))
                .isInstanceOf(PracticeCoachException.class)
                .hasMessageContaining("Ollama");

        // ユーザー発話は保存されるが、アシスタント発話は保存されない（save は 1 回だけ）
        verify(messageRepository, org.mockito.Mockito.times(1)).save(any(ChatMessage.class));
    }

    @Test
    void 終了済みセッションへの発言はConflict() {
        ChatSession ended = readySession();
        ended.setStatus(SessionStatus.ENDED);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(ended));

        assertThatThrownBy(() -> service.handleUserTurn(sessionId, "まだ話したいです", new RecordingListener()))
                .isInstanceOf(ConflictException.class);
        verify(messageRepository, org.mockito.Mockito.never()).save(any());
    }
}
