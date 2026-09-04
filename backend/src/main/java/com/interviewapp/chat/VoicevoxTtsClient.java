package com.interviewapp.chat;

import org.springframework.beans.factory.annotation.Value;
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

    public VoicevoxTtsClient(RestClient.Builder builder,
                             @Value("${app.voicevox.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public byte[] synthesize(String text, int speakerId) {
        if (text == null || text.isBlank()) {
            return new byte[0];
        }

        String audioQuery = restClient.post()
                .uri(uri -> uri.path("/audio_query")
                        .queryParam("text", text)
                        .queryParam("speaker", speakerId)
                        .build())
                .retrieve()
                .body(String.class);

        return restClient.post()
                .uri(UriComponentsBuilder.fromPath("/synthesis")
                        .queryParam("speaker", speakerId)
                        .toUriString())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(audioQuery)
                .retrieve()
                .body(byte[].class);
    }
}
