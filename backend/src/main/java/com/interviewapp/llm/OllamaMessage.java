package com.interviewapp.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Ollama {@code /api/chat} の1メッセージ(role/content)。 */
public record OllamaMessage(
        @JsonProperty("role") String role,
        @JsonProperty("content") String content) {

    public static OllamaMessage system(String content) {
        return new OllamaMessage("system", content);
    }

    public static OllamaMessage user(String content) {
        return new OllamaMessage("user", content);
    }

    public static OllamaMessage assistant(String content) {
        return new OllamaMessage("assistant", content);
    }
}
