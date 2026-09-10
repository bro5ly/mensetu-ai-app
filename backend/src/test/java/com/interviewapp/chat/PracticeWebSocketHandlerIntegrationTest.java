package com.interviewapp.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
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
import org.mockito.ArgumentCaptor;
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
        when(ttsClient.createAudioQuery(any(), anyInt())).thenReturn("{}");
        when(ttsClient.synthesizeFromQuery(any(), anyInt())).thenReturn(new byte[] {1, 2, 3, 4});

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
        // モックの応答は句点区切りで2文("なるほど。"/"そのとき何を意識しましたか？")のため、
        // 文単位でTTSを呼ぶ実装では合成・送信も2回に分かれる。
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(handler.binaryFrames).hasSize(2));
        assertThat(handler.binaryFrames).allSatisfy(frame -> assertThat(frame).containsExactly(1, 2, 3, 4));

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
        when(sttClient.transcribe(any(), any()))
                .thenReturn(TranscriptionResult.textOnly("アルバイトで新人教育を担当していました"));
        when(llm.streamReply(any(), any(), any())).thenReturn(Flux.just("いいですね。"));
        when(ttsClient.createAudioQuery(any(), anyInt())).thenReturn("{}");
        when(ttsClient.synthesizeFromQuery(any(), anyInt())).thenReturn(new byte[0]);

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

    @Test
    void 話速等の分析結果はLLMへの入力にだけ付記されユーザーへの表示には出ない() throws Exception {
        // 短時間(1.2秒)に対して文字数が多く、フィラーワードを含み、区間の間隔も長い
        // → 話速はFAST、フィラー2回、間2.1秒 いずれも「言及すべき特徴あり」の分析結果になる。
        String spokenText = "えーとえーと学生時代の話です";
        TranscriptionResult sttResult = new TranscriptionResult(spokenText, 1.2, List.of(
                new TranscriptionResult.Segment(0.0, 0.5),
                new TranscriptionResult.Segment(2.6, 3.0)));
        when(sttClient.transcribe(any(), any())).thenReturn(sttResult);
        when(llm.streamReply(any(), any(), any())).thenReturn(Flux.just("いいですね。"));
        when(ttsClient.createAudioQuery(any(), anyInt())).thenReturn("{}");
        when(ttsClient.synthesizeFromQuery(any(), anyInt())).thenReturn(new byte[0]);

        StandardWebSocketClient client = new StandardWebSocketClient();
        CollectingHandler handler = new CollectingHandler();
        WebSocketSession ws = client.execute(handler,
                "ws://localhost:" + port + "/ws/sessions/" + sessionId).get();

        ws.sendMessage(new BinaryMessage(new byte[] {10, 20, 30}));
        ws.sendMessage(new TextMessage("{\"type\":\"end_turn\"}"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(types(handler)).contains("assistant_message_end"));

        // ユーザーに見える transcript イベントには注記が含まれない(発話そのままのテキスト)。
        assertThat(handler.textFrames.stream().filter(f -> typeOf(f).equals("transcript")))
                .anySatisfy(f -> assertThat(f).contains(spokenText));
        assertThat(handler.textFrames.stream().filter(f -> typeOf(f).equals("transcript")))
                .noneSatisfy(f -> assertThat(f).contains("話し方の参考情報"));

        // LLMへの入力(userMessage)には注記が付記されている。
        ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(llm).streamReply(any(), any(), userMessageCaptor.capture());
        assertThat(userMessageCaptor.getValue())
                .startsWith(spokenText)
                .contains("[話し方の参考情報")
                .contains("速さがやや速め")
                .contains("つなぎ言葉が2回")
                .contains("間があった");

        // 保存される chat_messages.content にも注記は含まれない。
        List<ChatMessage> saved = messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId);
        assertThat(saved.get(0).getContent()).isEqualTo(spokenText);

        ws.close();
    }

    @Test
    void 録音中のプレビュー文字起こしはバッファをリセットせず本番の文字起こしに影響しない() throws Exception {
        when(sttClient.transcribe(any(), any()))
                .thenReturn(TranscriptionResult.textOnly("学生時代に"))
                .thenReturn(TranscriptionResult.textOnly("学生時代に力を入れたことです"));
        when(llm.streamReply(any(), any(), any())).thenReturn(Flux.just("いいですね。"));
        when(ttsClient.createAudioQuery(any(), anyInt())).thenReturn("{}");
        when(ttsClient.synthesizeFromQuery(any(), anyInt())).thenReturn(new byte[0]);

        StandardWebSocketClient client = new StandardWebSocketClient();
        CollectingHandler handler = new CollectingHandler();
        WebSocketSession ws = client.execute(handler,
                "ws://localhost:" + port + "/ws/sessions/" + sessionId).get();

        ws.sendMessage(new BinaryMessage(new byte[] {10, 20, 30}));
        ws.sendMessage(new TextMessage("{\"type\":\"request_partial_transcript\"}"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(types(handler)).contains("partial_transcript"));
        assertThat(handler.textFrames.stream().filter(f -> typeOf(f).equals("partial_transcript")))
                .anySatisfy(f -> assertThat(f).contains("学生時代に"));

        // プレビュー文字起こし後も録音は継続し、end_turn で改めて全体を文字起こしする
        ws.sendMessage(new BinaryMessage(new byte[] {40, 50}));
        ws.sendMessage(new TextMessage("{\"type\":\"end_turn\"}"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(types(handler)).contains("assistant_message_end"));
        assertThat(handler.textFrames.stream().filter(f -> typeOf(f).equals("transcript")))
                .anySatisfy(f -> assertThat(f).contains("学生時代に力を入れたことです"));

        ws.close();
    }

    @Test
    void プレビュー音声が無ければpartial_transcriptを送らない() throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        CollectingHandler handler = new CollectingHandler();
        WebSocketSession ws = client.execute(handler,
                "ws://localhost:" + port + "/ws/sessions/" + sessionId).get();

        ws.sendMessage(new TextMessage("{\"type\":\"request_partial_transcript\"}"));

        // 何も送られてこないことを、少し待ってから確認する(await では「起きないこと」の確認はしにくい)
        Thread.sleep(500);
        assertThat(types(handler)).doesNotContain("partial_transcript");

        ws.close();
    }
}
