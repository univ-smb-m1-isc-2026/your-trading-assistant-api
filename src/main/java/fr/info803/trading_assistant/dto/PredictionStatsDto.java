package fr.info803.trading_assistant.dto;

import java.math.BigDecimal;

public record PredictionStatsDto(
    BigDecimal min,
    BigDecimal max,
    BigDecimal mean,
    BigDecimal median,
    long count
) {
}
