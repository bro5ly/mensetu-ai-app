package com.interviewapp.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * VOICEVOX Engine への TTS クライアント。
 * {@code POST /audio_query} で読み上げクエリを作り、{@code POST /synthesis} で WAV を得る2段階。
 */
@Component
public class VoicevoxTtsClient implements TtsClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final double speedScale;

    public VoicevoxTtsClient(RestClient.Builder builder,
                             ObjectMapper objectMapper,
                             @Value("${app.voicevox.base-url}") String baseUrl,
                             @Value("${app.voicevox.speed-scale:1.1}") double speedScale) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.speedScale = speedScale;
    }

    @Override
    public String createAudioQuery(String text, int speakerId) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return restClient.post()
                .uri(uri -> uri.path("/audio_query")
                        .queryParam("text", text)
                        .queryParam("speaker", speakerId)
                        .build())
                .retrieve()
                .body(String.class);
    }

    @Override
    public byte[] synthesizeFromQuery(String audioQueryJson, int speakerId) {
        if (audioQueryJson == null || audioQueryJson.isBlank()) {
            return new byte[0];
        }
        return restClient.post()
                .uri(UriComponentsBuilder.fromPath("/synthesis")
                        .queryParam("speaker", speakerId)
                        .toUriString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(applySpeedScale(audioQueryJson))
                .retrieve()
                .body(byte[].class);
    }

    /**
     * 読み上げクエリの {@code speedScale} を上書きする。少し速く読ませることで、
     * 生成完了から読み上げ終わりまでの体感時間を縮める(既定1.1倍、自然さとのバランス)。
     * JSONの解析・書き換えに失敗した場合は元のクエリをそのまま使う(速度調整を諦めるだけで
     * 読み上げ自体は続行する)。
     */
    private String applySpeedScale(String audioQueryJson) {
        try {
            JsonNode node = objectMapper.readTree(audioQueryJson);
            if (node instanceof ObjectNode objectNode) {
                objectNode.put("speedScale", speedScale);
                return objectMapper.writeValueAsString(objectNode);
            }
            return audioQueryJson;
        } catch (Exception e) {
            return audioQueryJson;
        }
    }
}
