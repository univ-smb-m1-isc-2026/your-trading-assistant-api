package fr.info803.trading_assistant.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import fr.info803.trading_assistant.dto.PredictionFeaturesDto;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetDailyValue;

@DisplayName("PredictionFeatureService Unit Tests")
class PredictionFeatureServiceTest {

    private final PredictionFeatureService service = new PredictionFeatureService();

    @Nested
    @DisplayName("calculateForLatestCandle")
    class CalculateForLatestCandleTests {

        @Test
        @DisplayName("should compute deterministic features for flat market data")
        void shouldComputeDeterministicFeaturesForFlatMarketData() {
            List<AssetDailyValue> candles = buildFlatCandles(60, LocalDate.of(2026, 1, 30));

            PredictionFeaturesDto features = service.calculateForLatestCandle(candles);

            assertThat(features.return1d()).isEqualByComparingTo("0");
            assertThat(features.return2d()).isEqualByComparingTo("0");
            assertThat(features.return3d()).isEqualByComparingTo("0");
            assertThat(features.return5d()).isEqualByComparingTo("0");
            assertThat(features.return10d()).isEqualByComparingTo("0");
            assertThat(features.return20d()).isEqualByComparingTo("0");

            assertThat(features.closeVsMa5()).isEqualByComparingTo("0");
            assertThat(features.closeVsMa10()).isEqualByComparingTo("0");
            assertThat(features.closeVsMa20()).isEqualByComparingTo("0");
            assertThat(features.closeVsMa50()).isEqualByComparingTo("0");

            assertThat(features.volatility5()).isEqualByComparingTo("0");
            assertThat(features.volatility10()).isEqualByComparingTo("0");
            assertThat(features.volatility20()).isEqualByComparingTo("0");

            assertThat(features.volumeRatio5()).isEqualByComparingTo("1");
            assertThat(features.volumeRatio20()).isEqualByComparingTo("1");

            assertThat(features.highLowRange()).isEqualByComparingTo("0.1");
            assertThat(features.openGap()).isEqualByComparingTo("0");
            assertThat(features.rsi14()).isEqualByComparingTo("50");
            assertThat(features.macdSignalDiff()).isEqualByComparingTo("0");
            assertThat(features.bollingerPos()).isEqualByComparingTo("0.5");
            assertThat(features.atr14Pct()).isEqualByComparingTo("0.1");

            // 2026-03-30 (60 days from 2026-01-30 is 2026-03-30, which is Monday=1)
            assertThat(features.dayOfWeek()).isEqualTo(1);
        }

        @Test
        @DisplayName("should sort candles by date before computing")
        void shouldSortCandlesByDateBeforeComputing() {
            List<AssetDailyValue> candles = buildFlatCandles(60, LocalDate.of(2026, 1, 30));
            Collections.shuffle(candles);

            PredictionFeaturesDto features = service.calculateForLatestCandle(candles);

            assertThat(features.dayOfWeek()).isEqualTo(1);
            assertThat(features.return1d()).isEqualByComparingTo("0");
            assertThat(features.closeVsMa50()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("should throw when less than 50 candles are provided")
        void shouldThrowWhenLessThan50CandlesAreProvided() {
            List<AssetDailyValue> candles = buildFlatCandles(49, LocalDate.of(2026, 1, 30));

            assertThatThrownBy(() -> service.calculateForLatestCandle(candles))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least 50 candles");
        }
        
        @Test
        @DisplayName("should compute accurate features for trending market (Golden Values)")
        void shouldComputeAccurateFeaturesForTrendingMarket() {
            // Build 60 candles with an upward trend: close starts at 100, increases by 1 each day.
            // High = close + 2, Low = close - 2, Open = close - 1.
            // Volume = 1000 + i * 10
            List<AssetDailyValue> candles = buildTrendingCandles(60, LocalDate.of(2026, 1, 1));
            
            PredictionFeaturesDto features = service.calculateForLatestCandle(candles);
            
            // Last candle (index 59): Close = 159, Open = 158, High = 161, Low = 157, Vol = 1590
            
            // return_1d: (159 / 158) - 1 ≈ 0.006329
            assertThat(features.return1d()).isCloseTo(new BigDecimal("0.006329"), within(new BigDecimal("0.00001")));
            
            // return_5d: (159 / 154) - 1 ≈ 0.032467
            assertThat(features.return5d()).isCloseTo(new BigDecimal("0.032467"), within(new BigDecimal("0.00001")));
            
            // MA5 = (159+158+157+156+155)/5 = 157
            // close_vs_ma5 = (159 / 157) - 1 ≈ 0.012738
            assertThat(features.closeVsMa5()).isCloseTo(new BigDecimal("0.012738"), within(new BigDecimal("0.00001")));
            
            // open_gap: (open_t - close_{t-1}) / close_{t-1}
            // open_t = 158, close_{t-1} = 158 -> gap = 0
            assertThat(features.openGap()).isEqualByComparingTo("0");
            
            // high_low_range: (161 - 157) / 159 = 4 / 159 ≈ 0.025157
            assertThat(features.highLowRange()).isCloseTo(new BigDecimal("0.025157"), within(new BigDecimal("0.00001")));
            
            // RSI: strong uptrend, so RSI should be very high, close to 100
            assertThat(features.rsi14()).isGreaterThan(new BigDecimal("95"));
            
            // MACD may be near 0 initially due to the short window, let's assert it is greater than or equal to 0
            assertThat(features.macdSignalDiff()).isGreaterThanOrEqualTo(new BigDecimal("0"));
            
            // Bollinger Pos: closing above the upper band or near it because of constant trend
            // Upper band should be below current price if trend is steep enough, or position > 0.5
            assertThat(features.bollingerPos()).isGreaterThan(new BigDecimal("0.5"));
        }
    }

    private List<AssetDailyValue> buildFlatCandles(int size, LocalDate startDate) {
        Asset asset = Asset.builder().id(1L).symbol("BTC").build();
        List<AssetDailyValue> candles = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            candles.add(AssetDailyValue.builder()
                .id((long) (i + 1))
                .asset(asset)
                .date(startDate.plusDays(i))
                .open(new BigDecimal("100"))
                .high(new BigDecimal("105"))
                .low(new BigDecimal("95"))
                .close(new BigDecimal("100"))
                .volume(new BigDecimal("1000"))
                .build());
        }

        return candles;
    }
    
    private List<AssetDailyValue> buildTrendingCandles(int size, LocalDate startDate) {
        Asset asset = Asset.builder().id(1L).symbol("BTC").build();
        List<AssetDailyValue> candles = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            BigDecimal close = new BigDecimal(100 + i);
            BigDecimal open = close.subtract(BigDecimal.ONE);
            BigDecimal high = close.add(new BigDecimal("2"));
            BigDecimal low = close.subtract(new BigDecimal("2"));
            BigDecimal volume = new BigDecimal(1000 + (i * 10));
            
            candles.add(AssetDailyValue.builder()
                .id((long) (i + 1))
                .asset(asset)
                .date(startDate.plusDays(i))
                .open(open)
                .high(high)
                .low(low)
                .close(close)
                .volume(volume)
                .build());
        }

        return candles;
    }
}
