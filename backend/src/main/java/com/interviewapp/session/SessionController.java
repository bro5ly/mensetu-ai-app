package com.interviewapp.session;

import com.interviewapp.session.SessionDtos.PracticeEndResponse;
import com.interviewapp.session.SessionDtos.SessionDetail;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/{sessionId}")
    public SessionDetail get(@PathVariable UUID sessionId) {
        return sessionService.getDetail(sessionId);
    }

    @PostMapping("/{sessionId}/end")
    public PracticeEndResponse end(@PathVariable UUID sessionId) {
        return sessionService.endByUser(sessionId);
    }
}
