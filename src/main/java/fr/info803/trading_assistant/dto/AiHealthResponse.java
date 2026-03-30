package fr.info803.trading_assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response expected from the AI health API.
 * Example: {"status": "ok"}
 */
public record AiHealthResponse(
    @JsonProperty("status")
    String status
) {}
