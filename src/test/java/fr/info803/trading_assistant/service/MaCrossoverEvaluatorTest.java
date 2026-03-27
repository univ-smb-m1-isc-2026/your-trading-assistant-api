package fr.info803.trading_assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fr.info803.trading_assistant.entity.Alert;
import fr.info803.trading_assistant.entity.AlertDirection;
import fr.info803.trading_assistant.entity.AlertType;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetDailyValue;
import fr.info803.trading_assistant.repository.AssetDailyValueRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("MaCrossoverEvaluator Unit Tests")
class MaCrossoverEvaluatorTest {

    @Mock
    private AssetDailyValueRepository assetDailyValueRepository;

    @InjectMocks
    private MaCrossoverEvaluator evaluator;

    private Asset asset;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        asset = Asset.builder().id(1L).symbol("BTC").build();
        today = LocalDate.of(2026, 3, 27);
    }

    @Nested
    @DisplayName("supports()")
    class SupportsTests {
        @Test
        @DisplayName("should return true for MA_CROSSOVER")
        void shouldReturnTrueForMaCrossover() {
            assertThat(evaluator.supports(AlertType.MA_CROSSOVER)).isTrue();
        }

        @Test
        @DisplayName("should return false for other types")
        void shouldReturnFalseForOtherTypes() {
            assertThat(evaluator.supports(AlertType.PRICE_THRESHOLD)).isFalse();
            assertThat(evaluator.supports(AlertType.VOLUME_THRESHOLD)).isFalse();
        }
    }

    @Nested
    @DisplayName("evaluate() - SMA Crossover")
    class EvaluateSmaCrossoverTests {

        @Test
        @DisplayName("should trigger ABOVE (Golden Cross) when short crosses above long")
        void shouldTriggerGoldenCross() {
            // Arrange
            Alert alert = buildAlert(AlertDirection.ABOVE, 2, 3, "SMA");
            AssetDailyValue candle = buildCandle(today, "100");

            List<AssetDailyValue> history = new ArrayList<>();
            // J-3: close 10 => short(J-2) n/a, long(J-2) n/a
            history.add(buildCandle(today.minusDays(3), "10"));
            // J-2: close 20 => short=15, long n/a
            history.add(buildCandle(today.minusDays(2), "20"));
            // J-1: close 30 => short=25, long=20. Short(25) > Long(20). Let's adjust to make it cross ABOVE on J.
            // On J-1, short must be < long
            // On J, short must be >= long
            history.clear();
            
            // J-3
            history.add(buildCandle(today.minusDays(3), "50"));
            // J-2
            history.add(buildCandle(today.minusDays(2), "40"));
            // J-1
            history.add(buildCandle(today.minusDays(1), "10")); 
            // SMA(2) at J-1 = (40+10)/2 = 25
            // SMA(3) at J-1 = (50+40+10)/3 = 33.33
            // short(25) < long(33.33) -> no cross yet
            
            // J
            history.add(candle); // close 100
            // SMA(2) at J = (10+100)/2 = 55
            // SMA(3) at J = (40+10+100)/3 = 50
            // short(55) >= long(50) -> CROSSED ABOVE
            
            when(assetDailyValueRepository.findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                    eq(asset), any(LocalDate.class))).thenReturn(history);

            // Act
            Optional<BigDecimal> result = evaluator.evaluate(alert, candle);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo(new BigDecimal("55"));
        }

        @Test
        @DisplayName("should trigger BELOW (Death Cross) when short crosses below long")
        void shouldTriggerDeathCross() {
            // Arrange
            Alert alert = buildAlert(AlertDirection.BELOW, 2, 3, "SMA");
            AssetDailyValue candle = buildCandle(today, "10");

            List<AssetDailyValue> history = new ArrayList<>();
            // J-3
            history.add(buildCandle(today.minusDays(3), "10"));
            // J-2
            history.add(buildCandle(today.minusDays(2), "20"));
            // J-1
            history.add(buildCandle(today.minusDays(1), "50")); 
            // SMA(2) at J-1 = (20+50)/2 = 35
            // SMA(3) at J-1 = (10+20+50)/3 = 26.66
            // short(35) > long(26.66) -> no cross yet
            
            // J
            history.add(candle); // close 10
            // SMA(2) at J = (50+10)/2 = 30
            // SMA(3) at J = (20+50+10)/3 = 26.66
            // Wait, short(30) is still > long(26.66). Let's adjust close to 1.
            
            history.remove(history.size() - 1);
            candle = buildCandle(today, "5");
            history.add(candle);
            // SMA(2) at J = (50+5)/2 = 27.5
            // SMA(3) at J = (20+50+5)/3 = 25
            // Still >. Close to 1!
            
            history.remove(history.size() - 1);
            candle = buildCandle(today, "1");
            history.add(candle);
            // SMA(2) at J = (50+1)/2 = 25.5
            // SMA(3) at J = (20+50+1)/3 = 23.66
            // Still >! Needs a bigger drop or different setup.
            
            history.clear();
            // J-3
            history.add(buildCandle(today.minusDays(3), "10"));
            // J-2
            history.add(buildCandle(today.minusDays(2), "10"));
            // J-1
            history.add(buildCandle(today.minusDays(1), "100")); 
            // SMA(2) at J-1 = (10+100)/2 = 55
            // SMA(3) at J-1 = (10+10+100)/3 = 40
            // short(55) > long(40)
            
            // J
            candle = buildCandle(today, "10");
            history.add(candle);
            // SMA(2) at J = (100+10)/2 = 55
            // SMA(3) at J = (10+100+10)/3 = 40
            // Still >!
            
            // Let's use simpler numbers:
            // J-3: 100
            // J-2: 100
            // J-1: 100
            // SMA(2) at J-1 = 100, SMA(3) at J-1 = 100. (Not strictly >).
            
            // Golden cross requirement for Death Cross: J-1 short > long. J short <= long.
            history.clear();
            history.add(buildCandle(today.minusDays(3), "10"));
            history.add(buildCandle(today.minusDays(2), "100"));
            history.add(buildCandle(today.minusDays(1), "100"));
            // J-1: SMA(2)=100, SMA(3)=70. short > long.
            
            candle = buildCandle(today, "10");
            history.add(candle);
            // J: SMA(2)=(100+10)/2 = 55. SMA(3)=(100+100+10)/3 = 70.
            // J: short(55) <= long(70). CROSSED BELOW!
            
            when(assetDailyValueRepository.findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                    eq(asset), any(LocalDate.class))).thenReturn(history);

            // Act
            Optional<BigDecimal> result = evaluator.evaluate(alert, candle);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo(new BigDecimal("55"));
        }

        @Test
        @DisplayName("should return empty when no crossover occurs")
        void shouldReturnEmptyWhenNoCross() {
            // Arrange
            Alert alert = buildAlert(AlertDirection.ABOVE, 2, 3, "SMA");
            AssetDailyValue candle = buildCandle(today, "10");

            List<AssetDailyValue> history = new ArrayList<>();
            history.add(buildCandle(today.minusDays(3), "10"));
            history.add(buildCandle(today.minusDays(2), "10"));
            history.add(buildCandle(today.minusDays(1), "10"));
            history.add(candle);
            
            when(assetDailyValueRepository.findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                    eq(asset), any(LocalDate.class))).thenReturn(history);

            // Act
            Optional<BigDecimal> result = evaluator.evaluate(alert, candle);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty when not enough data")
        void shouldReturnEmptyWhenNotEnoughData() {
            Alert alert = buildAlert(AlertDirection.ABOVE, 10, 50, "SMA");
            AssetDailyValue candle = buildCandle(today, "100");

            // Only 10 candles instead of 51
            List<AssetDailyValue> history = new ArrayList<>();
            for (int i = 9; i >= 0; i--) {
                history.add(buildCandle(today.minusDays(i), "100"));
            }

            when(assetDailyValueRepository.findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                    eq(asset), any(LocalDate.class))).thenReturn(history);

            Optional<BigDecimal> result = evaluator.evaluate(alert, candle);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("computeSmaAtIndex and computeEmaAtIndex helpers")
    class HelpersTests {

        @Test
        void computeSmaAtIndexShouldReturnCorrectResult() {
            List<AssetDailyValue> candles = List.of(
                buildCandle(today.minusDays(2), "10"),
                buildCandle(today.minusDays(1), "20"),
                buildCandle(today, "30")
            );

            // SMA(2) at index 2 (values 20, 30) => 25
            BigDecimal sma = evaluator.computeSmaAtIndex(candles, 2, 2);
            assertThat(sma).isEqualByComparingTo("25");

            // Not enough data
            BigDecimal smaMissing = evaluator.computeSmaAtIndex(candles, 0, 2);
            assertThat(smaMissing).isNull();
        }
        
        @Test
        void computeEmaAtIndexShouldReturnCorrectResult() {
            List<AssetDailyValue> candles = List.of(
                buildCandle(today.minusDays(2), "10"), // index 0
                buildCandle(today.minusDays(1), "20"), // index 1
                buildCandle(today, "30")               // index 2
            );

            // EMA(2), period=2. 
            // k = 2 / (2+1) = 2/3.
            // Initial SMA at index 1 = (10+20)/2 = 15.
            // EMA at index 2 = 30 * (2/3) + 15 * (1/3) = 20 + 5 = 25.
            BigDecimal ema = evaluator.computeEmaAtIndex(candles, 2, 2);
            // Due to precision, let's just check the approximate or exact value if simple
            // 30 * 0.6666666666666667 + 15 * 0.3333333333333333
            // 20.0000000000000010 + 4.9999999999999995 = 25
            assertThat(ema).isEqualByComparingTo("25");
        }
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private Alert buildAlert(AlertDirection direction, int shortPeriod, int longPeriod, String maType) {
        return Alert.builder()
            .id(1L)
            .asset(asset)
            .type(AlertType.MA_CROSSOVER)
            .direction(direction)
            .shortPeriod(shortPeriod)
            .longPeriod(longPeriod)
            .maType(maType)
            .recurring(true)
            .active(true)
            .build();
    }

    private AssetDailyValue buildCandle(LocalDate date, String close) {
        return AssetDailyValue.builder()
            .id(1L)
            .asset(asset)
            .date(date)
            .open(BigDecimal.ZERO)
            .high(BigDecimal.ZERO)
            .low(BigDecimal.ZERO)
            .close(new BigDecimal(close))
            .volume(BigDecimal.ZERO)
            .build();
    }
}
