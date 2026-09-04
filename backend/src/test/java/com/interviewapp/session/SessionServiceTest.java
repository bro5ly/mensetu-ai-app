package com.interviewapp.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.interviewapp.common.NotFoundException;
import com.interviewapp.session.SessionDtos.PracticeEndResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private ChatSessionRepository sessionRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @InjectMocks
    private SessionService sessionService;

    private ChatMessage message(MessageRole role, MessageType type, int seq) {
        return new ChatMessage(UUID.randomUUID(), role, "text", type, seq);
    }

    @Test
    void 練習セッションのユーザー終了でENDEDになり軽いサマリーが付く() {
        UUID sessionId = UUID.randomUUID();
        ChatSession session = ChatSession.newPractice(UUID.randomUUID(), UUID.randomUUID());
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId)).thenReturn(List.of(
                message(MessageRole.USER, MessageType.NORMAL, 1),
                message(MessageRole.ASSISTANT, MessageType.ADVICE, 2),
                message(MessageRole.USER, MessageType.NORMAL, 3)));

        PracticeEndResponse response = sessionService.endByUser(sessionId);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ENDED);
        assertThat(session.getEndedAt()).isNotNull();
        assertThat(response.messageCount()).isEqualTo(3);
        assertThat(response.lightSummary()).contains("あなたの発言は 2 回", "アドバイスは 1 回");
    }

    @Test
    void 既に終了済みのセッションを再度終了しても状態を上書きしない() {
        UUID sessionId = UUID.randomUUID();
        ChatSession session = ChatSession.newPractice(UUID.randomUUID(), UUID.randomUUID());
        session.setStatus(SessionStatus.ENDED);
        session.setLightSummary("既存サマリー");
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId)).thenReturn(List.of());

        PracticeEndResponse response = sessionService.endByUser(sessionId);

        assertThat(response.lightSummary()).isEqualTo("既存サマリー");
    }

    @Test
    void 本番モードのセッション終了は本フェーズでは拒否される() {
        UUID sessionId = UUID.randomUUID();
        ChatSession session = new ChatSession();
        session.setMode(SessionMode.MOCK);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.endByUser(sessionId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 存在しないセッションはNotFound() {
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getDetail(sessionId))
                .isInstanceOf(NotFoundException.class);
    }
}
