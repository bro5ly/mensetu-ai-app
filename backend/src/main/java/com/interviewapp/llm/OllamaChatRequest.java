package com.interviewapp.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/** Ollama {@code POST /api/chat} のリクエストボディ。 */
record OllamaChatRequest(
        @JsonProperty("model") String model,
        @JsonProperty("messages") List<OllamaMessage> messages,
        @JsonProperty("stream") boolean stream,
        @JsonProperty("think") boolean think,
        @JsonProperty("options") Map<String, Object> options) {
}
