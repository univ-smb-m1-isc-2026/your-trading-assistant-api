package fr.info803.trading_assistant.entity;

import lombok.Getter;

@Getter
public enum ChartPatternType {
    // Bullish
    BULLISH_ENGULFING(ChartPatternCategory.BULLISH),
    MORNING_STAR(ChartPatternCategory.BULLISH),
    HAMMER(ChartPatternCategory.BULLISH),
    DRAGONFLY_DOJI(ChartPatternCategory.BULLISH),
    DOUBLE_BOTTOM(ChartPatternCategory.BULLISH),
    INVERSE_HEAD_AND_SHOULDERS(ChartPatternCategory.BULLISH),

    // Bearish
    BEARISH_ENGULFING(ChartPatternCategory.BEARISH),
    EVENING_STAR(ChartPatternCategory.BEARISH),
    SHOOTING_STAR(ChartPatternCategory.BEARISH),
    GRAVESTONE_DOJI(ChartPatternCategory.BEARISH),
    DOUBLE_TOP(ChartPatternCategory.BEARISH),
    HEAD_AND_SHOULDERS(ChartPatternCategory.BEARISH),

    // Neutral
    SMALL_RANGED_CANDLE(ChartPatternCategory.NEUTRAL),
    DOJI(ChartPatternCategory.NEUTRAL),
    SMALL_BODIED_CANDLE(ChartPatternCategory.NEUTRAL),
    TRIANGLE(ChartPatternCategory.NEUTRAL),
    WEDGE(ChartPatternCategory.NEUTRAL);

    private final ChartPatternCategory category;

    ChartPatternType(ChartPatternCategory category) {
        this.category = category;
    }
}
