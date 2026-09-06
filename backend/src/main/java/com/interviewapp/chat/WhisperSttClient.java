package com.interviewapp.chat;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * faster-whisper-server（OpenAI 互換 API）への STT クライアント。
 * {@code POST /v1/audio/transcriptions} にマルチパートで音声を送る。
 *
 * <p>{@code vad_filter=true} を必ず付ける。録音には話し始める前・話し終わった後の
 * 無音区間が混ざりがちで、無音/低音量区間をWhisperにそのまま渡すと、直前の学習データから
 * 全く無関係なフレーズ(例:「スタッフを見ることができます」)を繰り返し「幻覚」する既知の
 * 問題があるため、VAD(音声区間検出)で無音部分を除去してから文字起こしさせる。</p>
 */
@Component
public class WhisperSttClient implements SttClient {

    private static final Logger log = LoggerFactory.getLogger(WhisperSttClient.class);

    private final RestClient restClient;
    private final String model;

    public WhisperSttClient(RestClient.Builder builder,
                            @Value("${app.whisper.base-url}") String baseUrl,
                            @Value("${app.whisper.model:base}") String model) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.model = model;
    }

    @Override
    public String transcribe(byte[] audio, String contentType) {
        if (audio == null || audio.length == 0) {
            return "";
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return "audio" + extensionFor(contentType);
            }
        });
        body.add("model", model);
        body.add("language", "ja");
        body.add("response_format", "json");
        body.add("vad_filter", "true");

        try {
            Map<?, ?> response = restClient.post()
                    .uri("/v1/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            Object text = response == null ? null : response.get("text");
            return text == null ? "" : text.toString().trim();
        } catch (RuntimeException e) {
            // VADが音声区間を1つも検出できなかった場合など、サーバー側がエラーを返すことがある。
            // 呼び出し元は空文字を「音声を認識できませんでした」として自然に扱えるため、
            // ここで例外を握りつぶして処理を継続する。
            log.warn("音声の文字起こしに失敗しました（無音のみ等の可能性）: {}", e.getMessage());
            return "";
        }
    }

    private static String extensionFor(String contentType) {
        if (contentType == null) {
            return ".webm";
        }
        return switch (contentType.split(";")[0].trim()) {
            case "audio/wav", "audio/x-wav" -> ".wav";
            case "audio/mpeg" -> ".mp3";
            case "audio/ogg" -> ".ogg";
            case "audio/mp4", "audio/x-m4a" -> ".m4a";
            default -> ".webm";
        };
    }
}
