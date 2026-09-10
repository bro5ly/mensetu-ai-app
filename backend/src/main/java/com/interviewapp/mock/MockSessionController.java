package com.interviewapp.mock;

import com.interviewapp.mock.MockDtos.MockSessionStartResponse;
import com.interviewapp.mock.MockDtos.MockSessionSummaryResponse;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本番模擬面接(質問ごとに独立した一問一答形式、CLAUDE.md参照)のセッション開始・一覧。
 * {@code practice.PracticeSessionController}と同じく質問(interview_questions)単位のエンドポイント。
 */
@RestController
@RequestMapping("/api/questions/{questionId}/mock-sessions")
public class MockSessionController {

    private final MockSessionService mockSessionService;

    public MockSessionController(MockSessionService mockSessionService) {
        this.mockSessionService = mockSessionService;
    }

    /** 本番セッションを新規開始する(同じ質問で複数回挑戦できる)。 */
    @PostMapping
    public ResponseEntity<MockSessionStartResponse> start(@PathVariable UUID questionId) {
        MockSessionStartResponse response = mockSessionService.startSession(questionId);
        return ResponseEntity.created(URI.create("/api/sessions/" + response.id())).body(response);
    }

    /** その質問の過去の本番セッション一覧(スコア推移表示用)。 */
    @GetMapping
    public List<MockSessionSummaryResponse> list(@PathVariable UUID questionId) {
        return mockSessionService.listSessions(questionId);
    }
}
