package fr.info803.trading_assistant.dto;

import fr.info803.trading_assistant.entity.SentimentType;
import jakarta.validation.constraints.NotNull;

public record SentimentRequest(
        @NotNull(message = "Sentiment type is required")
        SentimentType type
) {
}
