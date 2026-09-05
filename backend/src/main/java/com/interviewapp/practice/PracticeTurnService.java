package com.interviewapp.practice;

import com.interviewapp.common.ConflictException;
import com.interviewapp.common.NotFoundException;
import com.interviewapp.company.Company;
import com.interviewapp.company.CompanyRepository;
import com.interviewapp.practice.AssistantMarkerParser.Result;
import com.interviewapp.question.InterviewQuestion;
import com.interviewapp.question.InterviewQuestionRepository;
import com.interviewapp.session.ChatMessage;
import com.interviewapp.session.ChatMessageRepository;
import com.interviewapp.session.ChatSession;
import com.interviewapp.session.ChatSessionRepository;
import com.interviewapp.session.MessageRole;
import com.interviewapp.session.MessageType;
import com.interviewapp.session.SessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 練習モードの1ターン（ユーザー発話 → コーチ応答）を処理する。
 *
 * <p>LLM のストリーミング中は DB トランザクションを保持したくないため、
 * 永続化は個々の save 呼び出し（各自トランザクション）に分割している。</p>
 */
@Service
public class PracticeTurnService {

    private static final Logger log = LoggerFactory.getLogger(PracticeTurnService.class);

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final CompanyRepository companyRepository;
    private final InterviewQuestionRepository questionRepository;
    private final PracticePromptFactory promptFactory;
    private final PracticeCoachLlm llm;
    private final AssistantMarkerParser markerParser;

    public PracticeTurnService(ChatSessionRepository sessionRepository,
                               ChatMessageRepository messageRepository,
                               CompanyRepository companyRepository,
                               InterviewQuestionRepository questionRepository,
                               PracticePromptFactory promptFactory,
                               PracticeCoachLlm llm,
                               AssistantMarkerParser markerParser) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.companyRepository = companyRepository;
        this.questionRepository = questionRepository;
        this.promptFactory = promptFactory;
        this.llm = llm;
        this.markerParser = markerParser;
    }

    /**
     * ユーザー発話を受け取り、コーチ応答をストリーミングで生成しながら listener に通知する。
     * ユーザー発話とコーチ応答の両方を {@code chat_messages} に保存する。
     */
    public ChatMessage handleUserTurn(UUID sessionId, String userText, PracticeTurnListener listener) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("セッションが見つかりません: " + sessionId));
        if (session.isEnded()) {
            throw new ConflictException("終了済みのセッションには発言できません: " + sessionId);
        }
        if (session.getStatus() == SessionStatus.READY) {
            session.setStatus(SessionStatus.IN_PROGRESS);
            session.setStartedAt(Instant.now());
            sessionRepository.save(session);
        }

        List<ChatMessage> history = messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId);
        int nextSeq = history.size();
        messageRepository.save(new ChatMessage(sessionId, MessageRole.USER, userText, MessageType.NORMAL, nextSeq));

        String systemPrompt = buildSystemPrompt(session);
        Result reply = streamReply(systemPrompt, promptFactory.toHistory(history), userText, listener);

        ChatMessage assistantMessage = new ChatMessage(
                sessionId, MessageRole.ASSISTANT, reply.content(), reply.messageType(), nextSeq + 1);
        return messageRepository.save(assistantMessage);
    }

    private String buildSystemPrompt(ChatSession session) {
        String companyName = companyRepository.findById(session.getCompanyId())
                .map(Company::getName)
                .orElse("(不明な会社)");
        String questionText = session.getQuestionId() == null ? "(質問未設定)"
                : questionRepository.findById(session.getQuestionId())
                        .map(InterviewQuestion::getQuestionText)
                        .orElse("(質問未設定)");
        return promptFactory.systemPrompt(companyName, questionText);
    }

    /**
     * LLM 応答をストリーミングし、冒頭の {@code <<ADVICE>>} マーカーを判定してから
     * マーカー除去済みの本文を listener に流す。
     */
    private Result streamReply(String systemPrompt, List<PracticeCoachLlm.Turn> history,
                               String userText, PracticeTurnListener listener) {
        StringBuilder raw = new StringBuilder();
        StringBuilder emitted = new StringBuilder();
        boolean[] decided = {false};
        MessageType[] type = {MessageType.NORMAL};

        try {
            for (String chunk : llm.streamReply(systemPrompt, history, userText).toIterable()) {
                if (chunk == null || chunk.isEmpty()) {
                    continue;
                }
                raw.append(chunk);

                if (!decided[0]) {
                    if (!markerParser.canDecideFrom(raw.toString())) {
                        continue;
                    }
                    Result soFar = markerParser.parse(raw.toString());
                    decided[0] = true;
                    type[0] = soFar.messageType();
                    listener.onAssistantStart(type[0]);
                    if (!soFar.content().isEmpty()) {
                        emitted.append(soFar.content());
                        listener.onAssistantChunk(soFar.content());
                    }
                    continue;
                }

                emitted.append(chunk);
                listener.onAssistantChunk(chunk);
            }
        } catch (PracticeCoachException e) {
            log.warn("LLM ストリーミングが失敗しました: {}", e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.error("LLM ストリーミング中にエラーが発生しました", e);
            throw e;
        }

        Result full = markerParser.parse(raw.toString());
        if (!decided[0]) {
            // 応答が極端に短くマーカー判定前に終わったケース
            listener.onAssistantStart(full.messageType());
            listener.onAssistantChunk(full.content());
        }
        listener.onAssistantEnd(full.messageType(), full.content());
        return full;
    }
}
