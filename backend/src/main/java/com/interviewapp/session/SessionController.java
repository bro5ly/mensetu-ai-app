package com.interviewapp.session;

import com.interviewapp.common.NotFoundException;
import com.interviewapp.mock.MockDtos.MockReportResponse;
import com.interviewapp.mock.MockSessionService;
import com.interviewapp.session.SessionDtos.SessionDetail;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final MockSessionService mockSessionService;

    public SessionController(SessionService sessionService, MockSessionService mockSessionService) {
        this.sessionService = sessionService;
        this.mockSessionService = mockSessionService;
    }

    @GetMapping("/{sessionId}")
    public SessionDetail get(@PathVariable UUID sessionId) {
        return sessionService.getDetail(sessionId);
    }

    /**
     * セッションを終了する(ユーザー強制終了)。
     * PRACTICE は同期的に軽いサマリーを200で返す。MOCK はレポート生成を非同期トリガーし
     * 202を返す(結果は{@code GET /api/sessions/{sessionId}/report}で取得する)。
     */
    @PostMapping("/{sessionId}/end")
    public ResponseEntity<?> end(@PathVariable UUID sessionId) {
        ChatSession session = sessionService.findOrThrow(sessionId);
        if (session.getMode() == SessionMode.MOCK) {
            return ResponseEntity.accepted().body(mockSessionService.endByUser(sessionId));
        }
        return ResponseEntity.ok(sessionService.endByUser(sessionId));
    }

    /** 本番模擬面接のレポート取得。生成完了後のみ200、未完了(生成中含む)は404。 */
    @GetMapping("/{sessionId}/report")
    public MockReportResponse getReport(@PathVariable UUID sessionId) {
        return mockSessionService.getReport(sessionId)
                .orElseThrow(() -> new NotFoundException("レポートはまだ準備できていません: " + sessionId));
    }
}
