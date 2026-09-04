package com.interviewapp.question;

import com.interviewapp.question.QuestionDtos.QuestionResponse;
import com.interviewapp.question.QuestionDtos.UpdateQuestionRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PatchMapping("/{questionId}")
    public QuestionResponse update(@PathVariable UUID questionId, @Valid @RequestBody UpdateQuestionRequest request) {
        return questionService.update(questionId, request);
    }

    @DeleteMapping("/{questionId}")
    public ResponseEntity<Void> delete(@PathVariable UUID questionId) {
        questionService.delete(questionId);
        return ResponseEntity.noContent().build();
    }
}
