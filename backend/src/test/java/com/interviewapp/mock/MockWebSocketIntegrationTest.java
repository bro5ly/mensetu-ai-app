package com.interviewapp.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewapp.chat.SttClient;
import com.interviewapp.chat.TtsClient;
import com.interviewapp.company.Company;
import com.interviewapp.company.CompanyRepository;
import com.interviewapp.mock.MockInterviewLlm.QuestionFeedback;
import com.interviewapp.mock.MockInterviewLlm.ReportResult;
import com.interviewapp.practice.PracticeCoachLlm;
import com.interviewapp.session.ChatSession;
import com.interviewapp.session.ChatSessionRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import reactor.core.publisher.Flux;

/**
 * 本番模擬面接のWebSocketフロー(冒頭の面接官発話 → 質問の進行 → 面接終了 → レポート生成)の
 * end-to-endテスト。{@code chat.PracticeWebSocketHandlerIntegrationTest}と同じ方式。
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MockWebSocketIntegrationTest {

    @LocalServerPort
    int port;

    @MockitoBean
    PracticeCoachLlm llm;
    @MockitoBean
    SttClient sttClient;
    @MockitoBean
    TtsClient ttsClient;
    @MockitoBean
    MockInterviewLlm mockLlm;

    @Autowired
    CompanyRepository companyRepository;
    @Autowired
    ChatSessionRepository sessionRepository;
    @Autowired
    MockSessionQuestionRepository questionRepository;
    @Autowired
    MockInterviewReportRepository reportRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(new Company("ABCコーポレーション", "・挑戦を後押しする文化がある"));
        ChatSession session = sessionRepository.save(ChatSession.newMock(company.getId(), UUID.randomUUID()));
        questionRepository.save(new MockSessionQuestion(session.getId(), "自己紹介をお願いします", "SELF_PR", 0));
        questionRepository.save(new MockSessionQuestion(session.getId(), "最後に何か質問はありますか", "REVERSE_QUESTION", 1));
        this.sessionId = session.getId();
    }

    @AfterEach
    void tearDown() {
        reportRepository.deleteAll();
        questionRepository.deleteAll();
        sessionRepository.deleteAll();
        companyRepository.deleteAll();
    }

    static final class CollectingHandler extends AbstractWebSocketHandler {
        final CopyOnWriteArrayList<String> textFrames = new CopyOnWriteArrayList<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            textFrames.add(message.getPayload());
        }
    }

    private List<String> types(CollectingHandler handler) {
        return handler.textFrames.stream().map(this::typeOf).toList();
    }

    private String typeOf(String json) {
        try {
            return objectMapper.readTree(json).path("type").asText();
        } catch (Exception e) {
            return "?";
        }
    }

    private List<String> framesOfType(CollectingHandler handler, String type) {
        return handler.textFrames.stream().filter(f -> typeOf(f).equals(type)).toList();
    }

    @Test
    void 接続すると最初の質問がLLM呼び出しなしでそのまま発話される() throws Exception {
        when(ttsClient.createAudioQuery(any(), anyInt())).thenReturn("{}");
        when(ttsClient.synthesizeFromQuery(any(), anyInt())).thenReturn(new byte[] {1});

        StandardWebSocketClient client = new StandardWebSocketClient();
        CollectingHandler handler = new CollectingHandler();
        client.execute(handler, "ws://localhost:" + port + "/ws/sessions/" + sessionId).get();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(types(handler)).contains("assistant_message_end"));

        assertThat(framesOfType(handler, "assistant_text_chunk"))
                .anySatisfy(f -> assertThat(f).contains("自己紹介をお願いします"));
        assertThat(types(handler)).doesNotContain("mock_question_advanced", "session_ended");
    }

    @Test
    void 質問を進めて最後の質問がQUESTION_DONEになるとレポート生成がトリガーされる() throws Exception {
        when(llm.streamReply(any(), any(), any()))
                .thenReturn(Flux.just("<<QUESTION_DONE>>"))
                .thenReturn(Flux.just("<<QUESTION_DONE>>"));
        when(ttsClient.createAudioQuery(any(), anyInt())).thenReturn("{}");
        when(ttsClient.synthesizeFromQuery(any(), anyInt())).thenReturn(new byte[] {1});
        when(mockLlm.generateReport(any(), any(), any()))
                .thenReturn(new ReportResult(75, "落ち着いて話せていた。",
                        List.of(new QuestionFeedback("自己紹介をお願いします", "ゼミ活動について話した", "経歴の説明にとどまっていた"))));

        StandardWebSocketClient client = new StandardWebSocketClient();
        CollectingHandler handler = new CollectingHandler();
        WebSocketSession ws = client.execute(handler, "ws://localhost:" + port + "/ws/sessions/" + sessionId).get();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(types(handler)).contains("assistant_message_end"));

        ws.sendMessage(new TextMessage("{\"type\":\"user_text\",\"text\":\"学生時代はゼミ活動に力を入れました\"}"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(types(handler)).contains("mock_question_advanced"));
        assertThat(framesOfType(handler, "mock_question_advanced"))
                .anySatisfy(f -> assertThat(f)
                        .contains("最後に何か質問はありますか")
                        .contains("\"questionIndex\":2")
                        .contains("\"totalQuestions\":2"));

        ws.sendMessage(new TextMessage("{\"type\":\"user_text\",\"text\":\"特にありません\"}"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(types(handler)).contains("report_ready"));
        assertThat(framesOfType(handler, "session_ended"))
                .anySatisfy(f -> assertThat(f).contains("\"reason\":\"AI_JUDGED\""));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var report = reportRepository.findBySessionId(sessionId);
            assertThat(report).isPresent();
            assertThat(report.get().getScore()).isEqualTo(75);
        });

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var closed = sessionRepository.findById(sessionId).orElseThrow();
            assertThat(closed.isEnded()).isTrue();
        });
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(ws.isOpen()).isFalse());
    }
}
