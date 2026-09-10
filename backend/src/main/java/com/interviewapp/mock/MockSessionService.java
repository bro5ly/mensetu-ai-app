package com.interviewapp.mock;

import com.interviewapp.common.NotFoundException;
import com.interviewapp.company.Company;
import com.interviewapp.company.CompanyRepository;
import com.interviewapp.mock.MockDtos.MockEndResponse;
import com.interviewapp.mock.MockDtos.MockReportResponse;
import com.interviewapp.mock.MockDtos.MockSessionStartResponse;
import com.interviewapp.mock.MockDtos.MockSessionSummaryResponse;
import com.interviewapp.mock.MockDtos.QuestionFeedbackResponse;
import com.interviewapp.mock.MockInterviewLlm.QuestionTranscript;
import com.interviewapp.question.InterviewQuestion;
import com.interviewapp.question.InterviewQuestionRepository;
import com.interviewapp.session.ChatMessage;
import com.interviewapp.session.ChatMessageRepository;
import com.interviewapp.session.ChatSession;
import com.interviewapp.session.ChatSessionRepository;
import com.interviewapp.session.EndedReason;
import com.interviewapp.session.MessageRole;
import com.interviewapp.session.SessionMode;
import com.interviewapp.session.SessionStatus;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 本番模擬面接のセッションライフサイクル(開始・一覧・終了・レポート取得)を扱う。
 *
 * <p>{@code CompanySourceService}で踏んだ教訓と同じ理由でクラスレベルの
 * {@code @Transactional}は付けない: LLM呼び出し(遅いネットワークI/O)の間DBコネクションを
 * 保持したくないため。各リポジトリ呼び出しはSpring Data JPAの暗黙のトランザクションに任せる。</p>
 */
@Service
public class MockSessionService {

    private static final Logger log = LoggerFactory.getLogger(MockSessionService.class);

    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final CompanyRepository companyRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final MockSessionQuestionRepository questionRepository;
    private final MockInterviewReportRepository reportRepository;
    private final MockQuestionFeedbackRepository feedbackRepository;
    private final MockInterviewLlm mockLlm;

    /** レポート生成(LLM呼び出し)専用の単一スレッドのプール。1本番セッションにつき1回だけ走る想定。 */
    private final ExecutorService reportExecutor = Executors.newSingleThreadExecutor();

    public MockSessionService(
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            CompanyRepository companyRepository,
            InterviewQuestionRepository interviewQuestionRepository,
            MockSessionQuestionRepository questionRepository,
            MockInterviewReportRepository reportRepository,
            MockQuestionFeedbackRepository feedbackRepository,
            MockInterviewLlm mockLlm) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.companyRepository = companyRepository;
        this.interviewQuestionRepository = interviewQuestionRepository;
        this.questionRepository = questionRepository;
        this.reportRepository = reportRepository;
        this.feedbackRepository = feedbackRepository;
        this.mockLlm = mockLlm;
    }

    @PreDestroy
    void shutdown() {
        reportExecutor.shutdown();
    }

    /**
     * 本番セッションを新規開始する。質問ごとに独立した一問一答形式(CLAUDE.md参照)のため、
     * 対象の{@code interview_questions}1件をそのまま{@link MockSessionQuestion}として
     * コピーするだけで、LLM呼び出しは行わない(自己紹介固定文言や質問バンクからの汎用質問
     * サンプリング、会社ならではの質問のLLM生成は、6問を組み合わせていた旧方式の名残で
     * いずれも撤廃した)。同じ質問で複数回挑戦できる(スコア推移を見られるように)。
     */
    public MockSessionStartResponse startSession(UUID questionId) {
        InterviewQuestion question = interviewQuestionRepository.findById(questionId)
                .orElseThrow(() -> new NotFoundException("質問が見つかりません: " + questionId));

        ChatSession session = sessionRepository.save(ChatSession.newMock(question.getCompanyId(), questionId));
        MockSessionQuestion saved = questionRepository.save(new MockSessionQuestion(
                session.getId(), question.getQuestionText(), question.getInternalCategory(), 0));
        return MockSessionStartResponse.from(session, List.of(saved));
    }

    /** その質問の過去の本番セッション一覧(新しい順。スコア推移表示用)。 */
    public List<MockSessionSummaryResponse> listSessions(UUID questionId) {
        return sessionRepository.findByQuestionIdAndModeOrderByCreatedAtDesc(questionId, SessionMode.MOCK).stream()
                .map(s -> new MockSessionSummaryResponse(
                        s.getId(), s.getStatus(), s.getEndedReason(),
                        reportRepository.findBySessionId(s.getId()).map(MockInterviewReport::getScore).orElse(null),
                        s.getStartedAt(), s.getEndedAt(), s.getCreatedAt()))
                .toList();
    }

    /** ユーザーによるセッション強制終了。レポート生成を非同期でトリガーする(結果は{@link #getReport}で取得)。 */
    public MockEndResponse endByUser(UUID sessionId) {
        ChatSession session = findOrThrow(sessionId);
        if (session.getMode() != SessionMode.MOCK) {
            throw new IllegalArgumentException("本番モードのセッションではありません: " + sessionId);
        }
        if (!session.isEnded()) {
            session.setStatus(SessionStatus.ENDED);
            session.setEndedReason(EndedReason.USER_ENDED);
            session.setEndedAt(Instant.now());
            sessionRepository.save(session);
        }
        triggerReportGeneration(sessionId);
        return new MockEndResponse(sessionId, "REPORT_PENDING");
    }

    public Optional<MockReportResponse> getReport(UUID sessionId) {
        return reportRepository.findBySessionId(sessionId).map(report -> {
            List<QuestionFeedbackResponse> feedbacks = feedbackRepository
                    .findByReportIdOrderBySequenceNoAsc(report.getId()).stream()
                    .map(QuestionFeedbackResponse::from)
                    .toList();
            return new MockReportResponse(
                    sessionId, report.getScore(), report.getAnswerTendencyAnalysis(), feedbacks, report.getCreatedAt());
        });
    }

    /**
     * レポート生成を非同期でトリガーする。既に生成済みなら何もしない(冪等。AI判断による終了と
     * ユーザー強制終了の両方から呼ばれうるため二重生成を防ぐ)。呼び出し元はこの
     * {@link CompletableFuture} の完了を使って(例: WebSocketの{@code report_ready}イベント)
     * 通知できる。
     */
    public CompletableFuture<Void> triggerReportGeneration(UUID sessionId) {
        if (reportRepository.findBySessionId(sessionId).isPresent()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> generateAndSaveReport(sessionId), reportExecutor)
                .exceptionally(e -> {
                    log.error("本番レポートの生成に失敗しました: sessionId={}", sessionId, e);
                    return null;
                });
    }

    private void generateAndSaveReport(UUID sessionId) {
        if (reportRepository.findBySessionId(sessionId).isPresent()) {
            return;
        }
        ChatSession session = findOrThrow(sessionId);
        Company company = companyRepository.findById(session.getCompanyId()).orElse(null);
        String companyName = company == null ? "(不明な会社)" : company.getName();
        String companyOverview = company == null ? null : company.getOverview();

        List<MockSessionQuestion> flow = questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId);
        List<ChatMessage> messages = messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId);

        List<QuestionTranscript> transcripts = new ArrayList<>();
        for (MockSessionQuestion question : flow) {
            String conversation = messages.stream()
                    .filter(m -> Objects.equals(m.getQuestionOrder(), question.getDisplayOrder()))
                    .map(m -> (m.getRole() == MessageRole.ASSISTANT ? "面接官: " : "候補者: ") + m.getContent())
                    .collect(Collectors.joining("\n"));
            if (!conversation.isBlank()) {
                transcripts.add(new QuestionTranscript(question.getQuestionText(), conversation));
            }
        }
        if (transcripts.isEmpty()) {
            log.warn("本番レポート生成: 会話が空のためスキップします sessionId={}", sessionId);
            return;
        }

        MockInterviewLlm.ReportResult result = mockLlm.generateReport(companyName, companyOverview, transcripts);
        int clampedScore = Math.max(MIN_SCORE, Math.min(MAX_SCORE, result.score()));
        MockInterviewReport report =
                reportRepository.save(new MockInterviewReport(sessionId, clampedScore, result.tendencyAnalysis()));

        int sequenceNo = 0;
        for (MockInterviewLlm.QuestionFeedback feedback : result.feedbacks()) {
            feedbackRepository.save(new MockQuestionFeedback(
                    report.getId(), feedback.questionText(), feedback.userAnswerSummary(), feedback.feedback(),
                    sequenceNo++));
        }
    }

    private ChatSession findOrThrow(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("セッションが見つかりません: " + sessionId));
    }
}
