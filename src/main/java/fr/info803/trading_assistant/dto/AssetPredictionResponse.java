package fr.info803.trading_assistant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import fr.info803.trading_assistant.entity.AssetPrediction;
import lombok.Builder;

@Builder
public record AssetPredictionResponse(
    Long id,
    String symbol,
    LocalDate date,
    BigDecimal predictedVariationPct,
    String expectedDirection
) {
    public static AssetPredictionResponse fromEntity(AssetPrediction entity) {
        String direction = entity.getPredictedVariation().compareTo(BigDecimal.ZERO) >= 0 ? "UP" : "DOWN";
        
        return AssetPredictionResponse.builder()
            .id(entity.getId())
            .symbol(entity.getAsset().getSymbol())
            .date(entity.getDate())
            .predictedVariationPct(entity.getPredictedVariation())
            .expectedDirection(direction)
            .build();
    }
}
