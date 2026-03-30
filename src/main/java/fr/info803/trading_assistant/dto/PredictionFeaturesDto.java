package fr.info803.trading_assistant.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PredictionFeaturesDto(

    @JsonProperty("return_1d")
    BigDecimal return1d,

    @JsonProperty("return_2d")
    BigDecimal return2d,

    @JsonProperty("return_3d")
    BigDecimal return3d,

    @JsonProperty("return_5d")
    BigDecimal return5d,

    @JsonProperty("return_10d")
    BigDecimal return10d,

    @JsonProperty("return_20d")
    BigDecimal return20d,

    @JsonProperty("close_vs_ma5")
    BigDecimal closeVsMa5,

    @JsonProperty("close_vs_ma10")
    BigDecimal closeVsMa10,

    @JsonProperty("close_vs_ma20")
    BigDecimal closeVsMa20,

    @JsonProperty("close_vs_ma50")
    BigDecimal closeVsMa50,

    @JsonProperty("volatility_5")
    BigDecimal volatility5,

    @JsonProperty("volatility_10")
    BigDecimal volatility10,

    @JsonProperty("volatility_20")
    BigDecimal volatility20,

    @JsonProperty("volume_ratio_5")
    BigDecimal volumeRatio5,

    @JsonProperty("volume_ratio_20")
    BigDecimal volumeRatio20,

    @JsonProperty("high_low_range")
    BigDecimal highLowRange,

    @JsonProperty("open_gap")
    BigDecimal openGap,

    @JsonProperty("rsi_14")
    BigDecimal rsi14,

    @JsonProperty("macd_signal_diff")
    BigDecimal macdSignalDiff,

    @JsonProperty("bollinger_pos")
    BigDecimal bollingerPos,

    @JsonProperty("atr_14_pct")
    BigDecimal atr14Pct,

    @JsonProperty("day_of_week")
    Integer dayOfWeek
) {
}
