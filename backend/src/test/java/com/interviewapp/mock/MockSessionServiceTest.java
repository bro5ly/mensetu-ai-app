package com.interviewapp.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewapp.common.NotFoundException;
import com.interviewapp.company.Company;
import com.interviewapp.company.CompanyRepository;
import com.interviewapp.mock.MockDtos.MockEndResponse;
import com.interviewapp.mock.MockDtos.MockReportResponse;
import com.interviewapp.mock.MockDtos.MockSessionStartResponse;
import com.interviewapp.mock.MockDtos.MockSessionSummaryResponse;
import com.interviewapp.mock.MockInterviewLlm.QuestionFeedback;
import com.interviewapp.mock.MockInterviewLlm.QuestionTranscript;
import com.interviewapp.mock.MockInterviewLlm.ReportResult;
import com.interviewapp.question.InterviewQuestion;
import com.interviewapp.question.InterviewQuestionRepository;
import com.interviewapp.session.ChatMessage;
import com.interviewapp.session.ChatMessageRepository;
import com.interviewapp.session.ChatSession;
import com.interviewapp.session.ChatSessionRepository;
import com.interviewapp.session.EndedReason;
import com.interviewapp.session.MessageRole;
import com.interviewapp.session.MessageType;
import com.interviewapp.session.SessionMode;
import com.interviewapp.session.SessionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MockSessionServiceTest {

    @Mock
    private ChatSessionRepository sessionRepository;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private InterviewQuestionRepository interviewQuestionRepository;
    @Mock
    private MockSessionQuestionRepository questionRepository;
    @Mock
    private MockInterviewReportRepository reportRepository;
    @Mock
    private MockQuestionFeedbackRepository feedbackRepository;
    @Mock
    private MockInterviewLlm mockLlm;

    private MockSessionService service;
    private final UUID companyId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MockSessionService(sessionRepository, messageRepository, companyRepository,
                interviewQuestionRepository, questionRepository, reportRepository, feedbackRepository, mockLlm);
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void 対象の質問をそのままコピーした1問だけのセッションを開始する() {
        InterviewQuestion question = new InterviewQuestion(companyId, "学生時代に力を入れたことは何ですか", "EXPERIENCE", 0);
        when(interviewQuestionRepository.findById(questionId)).thenReturn(Optional.of(question));
        lenient().when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MockSessionStartResponse response = service.startSession(questionId);

        assertThat(response.companyId()).isEqualTo(companyId);
        assertThat(response.status()).isEqualTo(SessionStatus.READY);
        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().get(0).questionText()).isEqualTo("学生時代に力を入れたことは何ですか");
        assertThat(response.questions().get(0).internalCategory()).isEqualTo("EXPERIENCE");
        assertThat(response.questions().get(0).displayOrder()).isZero();
    }

    @Test
    void 存在しない質問はNotFound() {
        when(interviewQuestionRepository.findById(questionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startSession(questionId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void セッション一覧はレポートがあればスコアを含む() {
        ChatSession ended = ChatSession.newMock(companyId, questionId);
        ended.setStatus(SessionStatus.ENDED);
        when(sessionRepository.findByQuestionIdAndModeOrderByCreatedAtDesc(questionId, SessionMode.MOCK))
                .thenReturn(List.of(ended));
        when(reportRepository.findBySessionId(ended.getId()))
                .thenReturn(Optional.of(new MockInterviewReport(ended.getId(), 82, "傾向分析です")));

        List<MockSessionSummaryResponse> summaries = service.listSessions(questionId);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).score()).isEqualTo(82);
    }

    @Test
    void セッション一覧はレポートが無ければスコアがnull() {
        ChatSession inProgress = ChatSession.newMock(companyId, questionId);
        when(sessionRepository.findByQuestionIdAndModeOrderByCreatedAtDesc(questionId, SessionMode.MOCK))
                .thenReturn(List.of(inProgress));
        when(reportRepository.findBySessionId(inProgress.getId())).thenReturn(Optional.empty());

        assertThat(service.listSessions(questionId).get(0).score()).isNull();
    }

    @Test
    void ユーザー終了でENDEDになりレポート生成がトリガーされる() throws Exception {
        ChatSession session = ChatSession.newMock(companyId, questionId);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        AtomicReference<MockInterviewReport> savedReportHolder = new AtomicReference<>();
        when(reportRepository.findBySessionId(sessionId)).thenAnswer(inv -> Optional.ofNullable(savedReportHolder.get()));
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of(
                new MockSessionQuestion(sessionId, "質問1", "SELF_PR", 0)));
        ChatMessage answer = new ChatMessage(sessionId, MessageRole.USER, "回答です", MessageType.NORMAL, 0);
        answer.setQuestionOrder(0);
        when(messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId)).thenReturn(List.of(answer));
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(new Company("ABC商事", null)));
        when(mockLlm.generateReport(any(), any(), any()))
                .thenReturn(new ReportResult(70, "傾向分析", List.of(new QuestionFeedback("質問1", "回答です", "F"))));
        when(reportRepository.save(any())).thenAnswer(inv -> {
            MockInterviewReport r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            savedReportHolder.set(r);
            return r;
        });

        MockEndResponse response = service.endByUser(sessionId);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ENDED);
        assertThat(session.getEndedReason()).isEqualTo(EndedReason.USER_ENDED);
        assertThat(response.status()).isEqualTo("REPORT_PENDING");

        // endByUser内でトリガーした非同期生成の完了を、単一スレッドの実行キューに同じ
        // sessionIdでもう一度積むことで(冪等なので実処理は増えない)完了を待つ代わりに使う。
        service.triggerReportGeneration(sessionId).get(5, TimeUnit.SECONDS);

        ArgumentCaptor<MockInterviewReport> savedReport = ArgumentCaptor.forClass(MockInterviewReport.class);
        verify(reportRepository, org.mockito.Mockito.times(1)).save(savedReport.capture());
        assertThat(savedReport.getValue().getScore()).isEqualTo(70);
    }

    @Test
    void MOCKでないセッションの終了は拒否される() {
        ChatSession practice = new ChatSession();
        practice.setMode(SessionMode.PRACTICE);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(practice));

        assertThatThrownBy(() -> service.endByUser(sessionId)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void レポートが無ければ空を返す() {
        when(reportRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());

        assertThat(service.getReport(sessionId)).isEmpty();
    }

    @Test
    void レポートがあれば質問ごとのフィードバック付きで返す() {
        MockInterviewReport report = new MockInterviewReport(sessionId, 85, "良い傾向です");
        report.setId(UUID.randomUUID());
        when(reportRepository.findBySessionId(sessionId)).thenReturn(Optional.of(report));
        when(feedbackRepository.findByReportIdOrderBySequenceNoAsc(report.getId())).thenReturn(List.of(
                new MockQuestionFeedback(report.getId(), "質問1", "回答要約", "フィードバック", 0)));

        Optional<MockReportResponse> response = service.getReport(sessionId);

        assertThat(response).isPresent();
        assertThat(response.get().score()).isEqualTo(85);
        assertThat(response.get().feedbacks()).hasSize(1);
        assertThat(response.get().feedbacks().get(0).questionText()).isEqualTo("質問1");
    }

    @Test
    void 既にレポートがあれば再生成しない() throws Exception {
        when(reportRepository.findBySessionId(sessionId))
                .thenReturn(Optional.of(new MockInterviewReport(sessionId, 60, "既存")));

        service.triggerReportGeneration(sessionId).get(5, TimeUnit.SECONDS);

        verify(mockLlm, never()).generateReport(any(), any(), any());
    }

    @Test
    void スコアは0から100の範囲にクランプされる() throws Exception {
        when(reportRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(ChatSession.newMock(companyId, questionId)));
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of(
                new MockSessionQuestion(sessionId, "質問1", "SELF_PR", 0)));
        ChatMessage answer = new ChatMessage(sessionId, MessageRole.USER, "回答", MessageType.NORMAL, 0);
        answer.setQuestionOrder(0);
        when(messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId)).thenReturn(List.of(answer));
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(new Company("ABC商事", null)));
        when(mockLlm.generateReport(any(), any(), any()))
                .thenReturn(new ReportResult(150, "傾向", List.of()));
        ArgumentCaptor<MockInterviewReport> savedReport = ArgumentCaptor.forClass(MockInterviewReport.class);
        when(reportRepository.save(savedReport.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.triggerReportGeneration(sessionId).get(5, TimeUnit.SECONDS);

        assertThat(savedReport.getValue().getScore()).isEqualTo(100);
    }

    @Test
    void 会話が空の質問しか無ければレポートを生成しない() throws Exception {
        when(reportRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(ChatSession.newMock(companyId, questionId)));
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of(
                new MockSessionQuestion(sessionId, "質問1", "SELF_PR", 0)));
        when(messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId)).thenReturn(List.of());
        lenient().when(companyRepository.findById(companyId)).thenReturn(Optional.of(new Company("ABC商事", null)));

        service.triggerReportGeneration(sessionId).get(5, TimeUnit.SECONDS);

        verify(mockLlm, never()).generateReport(any(), any(), any());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void 質問ごとの会話を面接官と候補者の発言としてまとめる() throws Exception {
        when(reportRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(ChatSession.newMock(companyId, questionId)));
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of(
                new MockSessionQuestion(sessionId, "自己紹介をお願いします", "SELF_PR", 0)));
        ChatMessage assistantMsg = new ChatMessage(sessionId, MessageRole.ASSISTANT, "自己紹介をどうぞ", MessageType.NORMAL, 0);
        assistantMsg.setQuestionOrder(0);
        ChatMessage userMsg = new ChatMessage(sessionId, MessageRole.USER, "ゼミ活動をしていました", MessageType.NORMAL, 1);
        userMsg.setQuestionOrder(0);
        when(messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId)).thenReturn(List.of(assistantMsg, userMsg));
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(new Company("ABC商事", null)));
        ArgumentCaptor<List<QuestionTranscript>> transcriptsCaptor = ArgumentCaptor.forClass(List.class);
        when(mockLlm.generateReport(any(), any(), transcriptsCaptor.capture()))
                .thenReturn(new ReportResult(70, "傾向", List.of()));
        when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.triggerReportGeneration(sessionId).get(5, TimeUnit.SECONDS);

        List<QuestionTranscript> transcripts = transcriptsCaptor.getValue();
        assertThat(transcripts).hasSize(1);
        assertThat(transcripts.get(0).conversation())
                .contains("面接官: 自己紹介をどうぞ")
                .contains("候補者: ゼミ活動をしていました");
    }
}
