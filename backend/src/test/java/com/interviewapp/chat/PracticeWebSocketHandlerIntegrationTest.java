package com.interviewapp.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewapp.company.Company;
import com.interviewapp.company.CompanyRepository;
import com.interviewapp.practice.PracticeCoachLlm;
import com.interviewapp.question.InterviewQuestion;
import com.interviewapp.question.InterviewQuestionRepository;
import com.interviewapp.session.ChatMessage;
import com.interviewapp.session.ChatMessageRepository;
import com.interviewapp.session.ChatSession;
import com.interviewapp.session.ChatSessionRepository;
import com.interviewapp.session.MessageRole;
import com.interviewapp.session.MessageType;
import com.interviewapp.session.SessionStatus;
import java.nio.ByteBuffer;
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
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import reactor.core.publisher.Flux;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PracticeWebSocketHandlerIntegrationTest {

    @LocalServerPort
    int port;

    @MockitoBean
    PracticeCoachLlm llm;
    @MockitoBean
    SttClient sttClient;
    @MockitoBean
    TtsClient ttsClient;

    @Autowired
    CompanyRepository companyRepository;
    @Autowired
    InterviewQuestionRepository questionRepository;
    @Autowired
    ChatSessionRepository sessionRepository;
    @Autowired
    ChatMessageRepository messageRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(new Company("ABCコーポレーション", null));
        InterviewQuestion question = questionRepository.save(
                new InterviewQuestion(company.getId(), "学生時代に力を入れたことを教えてください", "EXPERIENCE", 0));
        ChatSession session = sessionRepository.save(
                ChatSession.newPractice(company.getId(), question.getId()));
        this.sessionId = session.getId();
    }

    @AfterEach
    void tearDown() {
        messageRepository.deleteAll();
        sessionRepository.deleteAll();
        questionRepository.deleteAll();
        companyRepository.deleteAll();
    }

    static final class CollectingHandler extends AbstractWebSocketHandler {
        final CopyOnWriteArrayList<String> textFrames = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<byte[]> binaryFrames = new CopyOnWriteArrayList<>();
        volatile CloseStatus closeStatus;

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            textFrames.add(message.getPayload());
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
            ByteBuffer buf = message.getPayload();
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            binaryFrames.add(bytes);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            this.closeStatus = status;
        }
    }

    private List<String> types(CollectingHandler handler) {
        return handler.textFrames.stream().map(this::typeOf).toList();
    }

    private String typeOf(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.path("type").asText();
        } catch (Exception e) {
            return "?";
        }
    }

    @Test
    void テキスト発話から応答ストリームとTTSと終了までが流れる() throws Exception {
        when(llm.streamReply(any(), any(), any()))
                .thenReturn(Flux.just("なるほど。", "そのとき何を意識しましたか？"));
        when(ttsClient.synthesize(any(), anyInt())).thenReturn(new byte[] {1, 2, 3, 4});

        StandardWebSocketClient client = new StandardWebSocketClient();
        CollectingHandler handler = new CollectingHandler();
        WebSocketSession ws = client.execute(handler,
                "ws://localhost:" + port + "/ws/sessions/" + sessionId).get();

        ws.sendMessage(new TextMessage("{\"type\":\"user_text\",\"text\":\"サークルでリーダーをしていました\"}"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(types(handler)).contains("assistant_message_end"));

        assertThat(types(handler))
                .containsSubsequence("assistant_message_start", "assistant_text_chunk", "assistant_message_end");
        assertThat(handler.textFrames.stream().filter(f -> typeOf(f).equals("assistant_message_start")))
                .anySatisfy(f -> assertThat(f).contains("\"messageType\":\"NORMAL\""));
        assertThat(handler.binaryFrames).hasSize(1);
        assertThat(handler.binaryFrames.get(0)).containsExactly(1, 2, 3, 4);

        ws.sendMessage(new TextMessage("{\"type\":\"force_end\"}"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(types(handler)).contains("session_ended"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(handler.closeStatus).isNotNull());

        List<ChatMessage> saved = messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId);
        assertThat(saved).extracting(ChatMessage::getRole)
                .containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
        assertThat(saved.get(1).getContent()).isEqualTo("なるほど。そのとき何を意識しましたか？");
        assertThat(saved.get(1).getMessageType()).isEqualTo(MessageType.NORMAL);
        assertThat(sessionRepository.findById(sessionId)).get()
                .extracting(ChatSession::getStatus).isEqualTo(SessionStatus.ENDED);
    }

    @Test
    void 音声発話ではSTT結果がtranscriptとして返る() throws Exception {
        when(sttClient.transcribe(any(), any())).thenReturn("アルバイトで新人教育を担当していました");
        when(llm.streamReply(any(), any(), any())).thenReturn(Flux.just("いいですね。"));
        when(ttsClient.synthesize(any(), anyInt())).thenReturn(new byte[0]);

        StandardWebSocketClient client = new StandardWebSocketClient();
        CollectingHandler handler = new CollectingHandler();
        WebSocketSession ws = client.execute(handler,
                "ws://localhost:" + port + "/ws/sessions/" + sessionId).get();

        ws.sendMessage(new BinaryMessage(new byte[] {10, 20, 30}));
        ws.sendMessage(new TextMessage("{\"type\":\"end_turn\"}"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(types(handler)).contains("assistant_message_end"));

        assertThat(handler.textFrames.stream().filter(f -> typeOf(f).equals("transcript")))
                .anySatisfy(f -> assertThat(f).contains("アルバイトで新人教育を担当していました"));

        ws.close();
    }
}
