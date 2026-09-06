package com.interviewapp.chat;

/**
 * テキストを音声化する TTS クライアント。実体は VOICEVOX Engine への REST 呼び出し。
 *
 * <p>{@code audio_query}(テキスト解析、軽い)と {@code synthesis}(音声合成、重い)を
 * 別メソッドに分けている。呼び出し側(WebSocketハンドラ)が、文が確定した時点で
 * すぐに軽い{@link #createAudioQuery}を並行して走らせておき、重い{@link #synthesizeFromQuery}
 * だけを順番に実行することで、音声再生開始までの体感遅延を減らせるようにするため。</p>
 */
public interface TtsClient {

    /**
     * テキストを読み上げクエリ(モーラ・音素長などの解析結果)に変換する。
     * VOICEVOXの{@code /audio_query}に相当し、{@code /synthesis}より軽い処理。
     *
     * @param text     読み上げるテキスト
     * @param speakerId VOICEVOX の話者 ID
     * @return 読み上げクエリのJSON文字列(そのまま{@link #synthesizeFromQuery}に渡す)
     */
    String createAudioQuery(String text, int speakerId);

    /**
     * 読み上げクエリから実際の音声を合成する。VOICEVOXの{@code /synthesis}に相当し、
     * {@link #createAudioQuery}より重い処理。ローカルの単一エンジンに同時に投げすぎないよう、
     * 呼び出し側で直列化することを想定している。
     *
     * @param audioQueryJson {@link #createAudioQuery} の結果
     * @param speakerId      VOICEVOX の話者 ID
     * @return WAV 音声バイト列
     */
    byte[] synthesizeFromQuery(String audioQueryJson, int speakerId);
}
