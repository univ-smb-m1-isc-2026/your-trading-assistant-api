package fr.info803.trading_assistant.repository.projection;

import java.math.BigDecimal;

public interface AssetBacktestResultProjection {
    String getSymbol();
    Long getTotalPredictions();
    BigDecimal getSuccessRatePct();
    BigDecimal getMaxPotentialSuccessRatePct();
    BigDecimal getMeanAbsoluteErrorPct();
}
