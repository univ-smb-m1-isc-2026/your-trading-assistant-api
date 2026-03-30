package fr.info803.trading_assistant.dto;

import java.math.BigDecimal;

public record AssetBacktestResultDto(
    String symbol,
    Long totalPredictions,
    BigDecimal successRatePct,
    BigDecimal maxPotentialSuccessRatePct,
    BigDecimal meanAbsoluteErrorPct
) {}
