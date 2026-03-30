package fr.info803.trading_assistant.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response expected from the AI prediction API.
 * 
 * Example:
 * {
 *   "predicted_variation_pct": 0.28,
 *   "direction": "UP"
 * }
 */
public record AiPredictionResponse(
    @JsonProperty("predicted_variation_pct")
    BigDecimal predictedVariationPct,
    
    @JsonProperty("direction")
    String direction
) {}
