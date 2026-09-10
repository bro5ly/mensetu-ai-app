package com.interviewapp.mock;

import com.interviewapp.common.ConflictException;
import com.interviewapp.common.NotFoundException;
import com.interviewapp.company.Company;
import com.interviewapp.company.CompanyRepository;
import com.interviewapp.practice.PracticeCoachException;
import com.interviewapp.practice.PracticeCoachLlm;
import com.interviewapp.practice.PracticeCoachLlm.Turn;
import com.interviewapp.profile.UserProfileService;
import com.interviewapp.session.ChatMessage;
import com.interviewapp.session.ChatMessageRepository;
import com.interviewapp.session.ChatSession;
import com.interviewapp.session.ChatSessionRepository;
import com.interviewapp.session.EndedReason;
import com.interviewapp.session.MessageRole;
import com.interviewapp.session.MessageType;
import com.interviewapp.session.SessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 本番模擬面接(一問一答形式)の1問ごとの進行を処理する。
 *
 * <p>計量(小型)ローカルモデルには通しの会話進行(挨拶・相槌・次の質問への滑らかな
 * つなぎ)を任せるのが難しいと判断し、質問の提示・切り替えは常にコード側が
 * {@code mock_session_questions}の文面をそのまま話す(LLM呼び出しなし)。LLMに
 * 任せるのは、今の質問について「まだ聞くか・十分か」を判断しながら深掘りの質問を
 * 1つずつ重ねることだけに絞っている(1ターンの生成自体は practice モードと同じ
 * {@link PracticeCoachLlm} を再利用する)。質問ごとに完全に独立した一問一答として
 * 扱うため、LLM に渡す会話履歴も常に「今の質問」に属するやり取りだけに絞り込む
 * ({@link #historyForCurrentQuestion}) 。</p>
 *
 * <p>LLM のストリーミング中は DB トランザクションを保持したくないため、
 * 永続化は個々の save 呼び出し(各自トランザクション)に分割している
 * ({@code PracticeTurnService}と同じ方針)。</p>
 */
@Service
public class MockTurnService {

    private static final Logger log = LoggerFactory.getLogger(MockTurnService.class);

    /**
     * 1問あたりのフォローアップ(深掘り)の上限。質問ごとに独立して、AIが「十分」と
     * 判断する(応答が{@code <<QUESTION_DONE>>}マーカーだけになる)までは深掘りを
     * 続ける方針だが、小型ローカルモデルが判断を誤ったり忘れたりして深掘りを
     * 続けてしまうケースに備えた安全策(通常はこの上限に達する前にAIがマーカーを
     * 出して終える想定)。上限に達した場合はLLMを呼ばずに強制的に次の質問へ進める。
     */
    static final int MAX_FOLLOWUPS_PER_QUESTION = 5;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final CompanyRepository companyRepository;
    private final MockSessionQuestionRepository questionRepository;
    private final MockInterviewPromptFactory promptFactory;
    private final MockTurnMarkerParser markerParser;
    private final PracticeCoachLlm coachLlm;
    private final UserProfileService userProfileService;

    public MockTurnService(
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            CompanyRepository companyRepository,
            MockSessionQuestionRepository questionRepository,
            MockInterviewPromptFactory promptFactory,
            MockTurnMarkerParser markerParser,
            PracticeCoachLlm coachLlm,
            UserProfileService userProfileService) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.companyRepository = companyRepository;
        this.questionRepository = questionRepository;
        this.promptFactory = promptFactory;
        this.markerParser = markerParser;
        this.coachLlm = coachLlm;
        this.userProfileService = userProfileService;
    }

    /**
     * 面接冒頭、最初の質問(自己紹介の固定文言)をそのまま(LLM呼び出しなし)発話させる。
     * 既に開始済み(再接続等でREADYでなくなっている)場合は何もしない。
     */
    public void startInterview(UUID sessionId, MockTurnListener listener) {
        ChatSession session = findOrThrow(sessionId);
        if (session.isEnded()) {
            throw new ConflictException("終了済みのセッションです: " + sessionId);
        }
        if (session.getStatus() != SessionStatus.READY) {
            return;
        }
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setStartedAt(Instant.now());
        sessionRepository.save(session);

        List<MockSessionQuestion> flow = questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId);
        if (flow.isEmpty()) {
            throw new IllegalStateException("質問フローが見つかりません: " + sessionId);
        }
        askQuestion(sessionId, flow.get(0), 0, flow.size(), false, listener);
    }

    /** ユーザーの回答を受け取り、今の質問をさらに深掘りするか、次の質問(または面接終了)へ進めるかを判定する。 */
    public void handleUserTurn(UUID sessionId, String userText, MockTurnListener listener) {
        ChatSession session = findOrThrow(sessionId);
        if (session.isEnded()) {
            throw new ConflictException("終了済みのセッションには発言できません: " + sessionId);
        }

        List<MockSessionQuestion> flow = questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId);
        int currentIndex = session.getCurrentQuestionOrder();
        MockSessionQuestion currentQuestion = flow.get(currentIndex);

        List<ChatMessage> history = messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId);
        int nextSeq = history.size();
        ChatMessage userMessage = new ChatMessage(sessionId, MessageRole.USER, userText, MessageType.NORMAL, nextSeq);
        userMessage.setQuestionOrder(currentIndex);
        messageRepository.save(userMessage);

        // 既にフォローアップ上限まで使い切っている場合は、LLMを呼ばず強制的に次へ進める
        // (小型モデルの判断ミス・粘りに対する安全策。詳細はMAX_FOLLOWUPS_PER_QUESTION参照)。
        if (session.getCurrentQuestionFollowups() >= MAX_FOLLOWUPS_PER_QUESTION) {
            advanceOrEnd(session, flow, currentIndex, listener);
            return;
        }

        String systemPrompt = followUpSystemPrompt(session, currentQuestion, currentIndex, flow.size());
        // LLM に渡す履歴は「今の質問」に属するやり取りだけに絞る(質問ごとに完全に独立した
        // 一問一答として扱うため。historyForCurrentQuestion参照)。
        MockTurnMarkerParser.Result result =
                generateReply(systemPrompt, toHistory(historyForCurrentQuestion(history, currentIndex)), userText);

        if (result.done()) {
            advanceOrEnd(session, flow, currentIndex, listener);
            return;
        }

        String followUp = result.content();
        notifyListener(listener, followUp);
        ChatMessage assistantMessage =
                new ChatMessage(sessionId, MessageRole.ASSISTANT, followUp, MessageType.NORMAL, nextSeq + 1);
        assistantMessage.setQuestionOrder(currentIndex);
        messageRepository.save(assistantMessage);

        session.setCurrentQuestionFollowups(session.getCurrentQuestionFollowups() + 1);
        sessionRepository.save(session);
    }

    /** 今の質問への深掘りを終え、次の質問があればそのまま提示し、無ければ面接を終える。 */
    private void advanceOrEnd(ChatSession session, List<MockSessionQuestion> flow, int currentIndex,
                               MockTurnListener listener) {
        int nextIndex = currentIndex + 1;
        if (nextIndex >= flow.size()) {
            endInterview(session, listener);
            return;
        }
        session.setCurrentQuestionOrder(nextIndex);
        session.setCurrentQuestionFollowups(0);
        sessionRepository.save(session);
        askQuestion(session.getId(), flow.get(nextIndex), nextIndex, flow.size(), true, listener);
    }

    /**
     * 質問文をそのまま(LLM呼び出しなし)発話させ、保存する。{@code notifyAdvance}が
     * true のときだけ{@link MockTurnListener#onQuestionAdvance}を通知する(面接冒頭の
     * 最初の質問は「進んだ」わけではないため通知しない)。
     */
    private void askQuestion(UUID sessionId, MockSessionQuestion question, int index, int totalQuestions,
                              boolean notifyAdvance, MockTurnListener listener) {
        String text = question.getQuestionText();
        notifyListener(listener, text);

        List<ChatMessage> history = messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId);
        ChatMessage assistantMessage =
                new ChatMessage(sessionId, MessageRole.ASSISTANT, text, MessageType.NORMAL, history.size());
        assistantMessage.setQuestionOrder(index);
        messageRepository.save(assistantMessage);

        if (notifyAdvance) {
            listener.onQuestionAdvance(text, index + 1, totalQuestions);
        }
    }

    private String followUpSystemPrompt(ChatSession session, MockSessionQuestion currentQuestion,
                                         int currentIndex, int totalQuestions) {
        Company company = companyRepository.findById(session.getCompanyId()).orElse(null);
        String companyName = company == null ? "(不明な会社)" : company.getName();
        String companyOverview = company == null ? null : company.getOverview();
        String candidateProfile = userProfileService.getResumeTextOrNull();
        return promptFactory.followUpSystemPrompt(companyName, companyOverview, candidateProfile,
                currentQuestion.getQuestionText(), currentIndex + 1, totalQuestions);
    }

    /**
     * LLM 応答をストリーミングで受け取り切ってから、深掘りを終えるべきかを示すマーカーを判定する。
     * マーカーは応答全体として出力される想定(前後に文章が付かない)なので、practice モードの
     * 冒頭マーカーのような早期判定はできないが、本番モードの1回の深掘り応答はもともと短いため
     * 体感影響は小さい想定(CLAUDE.md参照)。
     */
    private MockTurnMarkerParser.Result generateReply(String systemPrompt, List<Turn> history, String userText) {
        StringBuilder raw = new StringBuilder();
        try {
            for (String chunk : coachLlm.streamReply(systemPrompt, history, userText).toIterable()) {
                if (chunk != null && !chunk.isEmpty()) {
                    raw.append(chunk);
                }
            }
        } catch (PracticeCoachException e) {
            log.warn("面接官 LLM のストリーミングが失敗しました: {}", e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.error("面接官 LLM のストリーミング中にエラーが発生しました", e);
            throw e;
        }

        return markerParser.parse(raw.toString());
    }

    /** 確定した本文を listener に通知する(mockモードは全文を溜めてから一括で通知する方針)。 */
    private static void notifyListener(MockTurnListener listener, String content) {
        listener.onAssistantStart();
        if (!content.isEmpty()) {
            listener.onAssistantChunk(content);
        }
        listener.onAssistantEnd(content);
    }

    /** 質問フローを全て終えたときの締めくくり(固定文言、LLM呼び出しなし)。 */
    private void endInterview(ChatSession session, MockTurnListener listener) {
        UUID sessionId = session.getId();
        String closing = MockInterviewPromptFactory.INTERVIEW_CLOSING_MESSAGE;
        notifyListener(listener, closing);
        List<ChatMessage> history = messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId);
        messageRepository.save(
                new ChatMessage(sessionId, MessageRole.ASSISTANT, closing, MessageType.NORMAL, history.size()));

        if (!session.isEnded()) {
            session.setStatus(SessionStatus.ENDED);
            session.setEndedReason(EndedReason.AI_JUDGED);
            session.setEndedAt(Instant.now());
            sessionRepository.save(session);
        }
        listener.onInterviewEnd();
    }

    private ChatSession findOrThrow(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("セッションが見つかりません: " + sessionId));
    }

    /** 保存済みメッセージ(古い順)を LLM 履歴に変換する。 */
    private static List<Turn> toHistory(List<ChatMessage> messages) {
        return messages.stream()
                .map(m -> new Turn(
                        m.getRole() == MessageRole.ASSISTANT ? Turn.Role.ASSISTANT : Turn.Role.USER,
                        m.getContent()))
                .toList();
    }

    /**
     * セッション全体の履歴から、今の質問(questionIndex)に属するやり取りだけを取り出す。
     * 質問ごとに完全に独立した一問一答として扱うため、面接官への1ターンの生成には常に
     * この絞り込んだ履歴だけを渡す(レポート生成は別経路で、質問ごとのグルーピングも
     * 兼ねて全件を扱う。ここでは変更しない)。
     */
    private static List<ChatMessage> historyForCurrentQuestion(List<ChatMessage> history, int questionIndex) {
        return history.stream()
                .filter(m -> Objects.equals(m.getQuestionOrder(), questionIndex))
                .toList();
    }
}
