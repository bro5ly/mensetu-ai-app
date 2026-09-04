package com.interviewapp.common;

import java.time.Instant;

/** エラーレスポンスの共通ボディ。 */
public record ApiError(int status, String error, String message, Instant timestamp) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, Instant.now());
    }
}
