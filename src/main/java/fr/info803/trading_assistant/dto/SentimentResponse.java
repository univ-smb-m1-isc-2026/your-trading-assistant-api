package fr.info803.trading_assistant.dto;

import fr.info803.trading_assistant.entity.SentimentType;
import java.time.LocalDateTime;

public record SentimentResponse(
        String symbol,
        SentimentType type,
        LocalDateTime updatedAt
) {
}
