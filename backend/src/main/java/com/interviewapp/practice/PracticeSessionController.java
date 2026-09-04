package com.interviewapp.practice;

import com.interviewapp.session.SessionDtos.PracticeSessionResponse;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/questions/{questionId}/practice-session")
public class PracticeSessionController {

    private final PracticeSessionService practiceSessionService;

    public PracticeSessionController(PracticeSessionService practiceSessionService) {
        this.practiceSessionService = practiceSessionService;
    }

    @PostMapping
    public ResponseEntity<PracticeSessionResponse> start(@PathVariable UUID questionId) {
        PracticeSessionResponse response = practiceSessionService.startOrResume(questionId);
        return ResponseEntity
                .created(URI.create("/api/sessions/" + response.id()))
                .body(response);
    }
}
