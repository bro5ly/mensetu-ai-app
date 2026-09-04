package com.interviewapp.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception ex) {
        String message = ex instanceof MethodArgumentNotValidException manv && manv.getFieldError() != null
                ? manv.getFieldError().getField() + " " + manv.getFieldError().getDefaultMessage()
                : ex.getMessage();
        return build(HttpStatus.BAD_REQUEST, message);
    }

    /** LLM/検索など上流依存が期待どおりの応答を返さなかったとき(例: 企業リサーチの生成失敗)。 */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleUpstream(IllegalStateException ex) {
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
