package com.interviewapp.practice;

/**
 * 練習コーチ役の LLM 呼び出しが失敗したことを表す。
 *
 * <p>{@code message} はそのままフロントに表示できる日本語の説明にすること
 * （WebSocket の {@code error} イベントとして送出される）。技術的な例外は {@code cause} に包む。</p>
 */
public class PracticeCoachException extends RuntimeException {

    public PracticeCoachException(String message, Throwable cause) {
        super(message, cause);
    }
}
