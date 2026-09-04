package com.interviewapp.common;

/** リソースが見つからない場合にスローする。GlobalExceptionHandler が 404 に変換する。 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
