package com.interviewapp.common;

/** リソースの状態が要求と矛盾する場合にスローする。GlobalExceptionHandler が 409 に変換する。 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
