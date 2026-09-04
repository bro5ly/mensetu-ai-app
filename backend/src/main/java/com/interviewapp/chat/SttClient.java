package com.interviewapp.chat;

/** 音声をテキスト化する STT クライアント。実体は faster-whisper への REST 呼び出し。 */
public interface SttClient {

    /**
     * 音声バイト列を文字起こしする。
     *
     * @param audio       録音データ（WebM/Opus 等）
     * @param contentType 音声の MIME タイプ
     * @return 文字起こし結果（空文字の場合あり）
     */
    String transcribe(byte[] audio, String contentType);
}
