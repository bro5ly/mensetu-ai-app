package com.interviewapp.session;

import com.interviewapp.common.NotFoundException;
import com.interviewapp.session.SessionDtos.PracticeEndResponse;
import com.interviewapp.session.SessionDtos.SessionDetail;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SessionService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    public SessionService(ChatSessionRepository sessionRepository, ChatMessageRepository messageRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public SessionDetail getDetail(UUID sessionId) {
        ChatSession session = findOrThrow(sessionId);
        List<ChatMessage> messages = messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId);
        return SessionDetail.from(session, messages);
    }

    /**
     * ユーザーによるセッション強制終了。
     * 練習モードは同期的に軽いサマリーを返す。本番モードは本フェーズ未対応。
     */
    public PracticeEndResponse endByUser(UUID sessionId) {
        ChatSession session = findOrThrow(sessionId);
        if (session.getMode() != SessionMode.PRACTICE) {
            throw new IllegalArgumentException("本番モードの終了は本フェーズでは未対応です");
        }

        List<ChatMessage> messages = messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId);
        if (!session.isEnded()) {
            session.setStatus(SessionStatus.ENDED);
            session.setEndedAt(Instant.now());
            session.setLightSummary(buildLightSummary(messages));
        }
        return new PracticeEndResponse(session.getId(), messages.size(), session.getLightSummary());
    }

    static String buildLightSummary(List<ChatMessage> messages) {
        long userTurns = messages.stream().filter(m -> m.getRole() == MessageRole.USER).count();
        long adviceCount = messages.stream().filter(m -> m.getMessageType() == MessageType.ADVICE).count();
        if (userTurns == 0) {
            return "まだ発言がないまま練習を終了しました。";
        }
        return "練習を終了しました。あなたの発言は %d 回、AIからのアドバイスは %d 回でした。"
                .formatted(userTurns, adviceCount);
    }

    @Transactional(readOnly = true)
    public ChatSession findOrThrow(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("セッションが見つかりません: " + sessionId));
    }
}
