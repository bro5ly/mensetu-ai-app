package com.interviewapp.chat;

/** テキストを音声化する TTS クライアント。実体は VOICEVOX Engine への REST 呼び出し。 */
public interface TtsClient {

    /**
     * テキストを読み上げ音声に変換する。
     *
     * @param text     読み上げるテキスト
     * @param speakerId VOICEVOX の話者 ID
     * @return WAV 音声バイト列
     */
    byte[] synthesize(String text, int speakerId);
}
