package com.interviewapp.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Ollama {@code POST /api/chat} の応答(非ストリーミング時は1件、ストリーミング時は行ごとに1件)。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaChatResponse(
        @JsonProperty("message") OllamaMessage message,
        @JsonProperty("done") boolean done,
        @JsonProperty("done_reason") String doneReason) {
}
