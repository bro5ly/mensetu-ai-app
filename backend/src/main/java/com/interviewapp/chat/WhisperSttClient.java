package com.interviewapp.chat;

import java.util.Map;
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
 */
@Component
public class WhisperSttClient implements SttClient {

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

        Map<?, ?> response = restClient.post()
                .uri("/v1/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(Map.class);

        Object text = response == null ? null : response.get("text");
        return text == null ? "" : text.toString().trim();
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
