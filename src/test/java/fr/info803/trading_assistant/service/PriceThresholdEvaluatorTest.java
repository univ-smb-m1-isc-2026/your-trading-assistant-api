package fr.info803.trading_assistant.service;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import fr.info803.trading_assistant.entity.Alert;
import fr.info803.trading_assistant.entity.AlertDirection;
import fr.info803.trading_assistant.entity.AlertType;
import fr.info803.trading_assistant.entity.AssetDailyValue;

/**
 * Unit tests for PriceThresholdEvaluator.
 *
 * Tests the Strategy Pattern implementation for PRICE_THRESHOLD alerts.
 * No mocks needed — PriceThresholdEvaluator is a pure function with no dependencies.
 *
 * Covers:
 * - supports() returns true only for PRICE_THRESHOLD
 * - ABOVE direction: triggers when high >= threshold, returns high value
 * - ABOVE direction: does not trigger when high < threshold
 * - ABOVE direction: triggers on exact boundary (high == threshold)
 * - BELOW direction: triggers when low <= threshold, returns low value
 * - BELOW direction: does not trigger when low > threshold
 * - BELOW direction: triggers on exact boundary (low == threshold)
 */
@DisplayName("PriceThresholdEvaluator Unit Tests")
class PriceThresholdEvaluatorTest {

    private PriceThresholdEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new PriceThresholdEvaluator();
    }

    // =========================================================================
    // supports()
    // =========================================================================

    @Nested
    @DisplayName("supports()")
    class SupportsTests {

        @Test
        @DisplayName("should return true for PRICE_THRESHOLD")
        void shouldReturnTrueForPriceThreshold() {
            assertThat(evaluator.supports(AlertType.PRICE_THRESHOLD)).isTrue();
        }

        @Test
        @DisplayName("should return false for VOLUME_THRESHOLD")
        void shouldReturnFalseForVolumeThreshold() {
            assertThat(evaluator.supports(AlertType.VOLUME_THRESHOLD)).isFalse();
        }
    }

    // =========================================================================
    // evaluate() — ABOVE direction
    // =========================================================================

    @Nested
    @DisplayName("evaluate() — ABOVE direction")
    class EvaluateAboveTests {

        @Test
        @DisplayName("should trigger when candle high >= threshold and return high value")
        void shouldTriggerWhenHighAboveThreshold() {
            // Arrange: threshold 100k, candle high 101k
            Alert alert = buildAlert(AlertDirection.ABOVE, "100000");
            AssetDailyValue candle = buildCandle("95000", "101000", "94000", "99000", "50000");

            // Act
            Optional<BigDecimal> result = evaluator.evaluate(alert, candle);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo(new BigDecimal("101000"));
        }

        @Test
        @DisplayName("should NOT trigger when candle high < threshold")
        void shouldNotTriggerWhenHighBelowThreshold() {
            // Arrange: threshold 100k, candle high 99k
            Alert alert = buildAlert(AlertDirection.ABOVE, "100000");
            AssetDailyValue candle = buildCandle("95000", "99000", "94000", "98000", "50000");

            // Act
            Optional<BigDecimal> result = evaluator.evaluate(alert, candle);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should trigger on exact boundary (high == threshold)")
        void shouldTriggerOnExactBoundary() {
            // Arrange: threshold == high == 100000
            Alert alert = buildAlert(AlertDirection.ABOVE, "100000");
            AssetDailyValue candle = buildCandle("95000", "100000", "94000", "99000", "50000");

            // Act
            Optional<BigDecimal> result = evaluator.evaluate(alert, candle);

            // Assert: >= includes equality
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo(new BigDecimal("100000"));
        }
    }

    // =========================================================================
    // evaluate() — BELOW direction
    // =========================================================================

    @Nested
    @DisplayName("evaluate() — BELOW direction")
    class EvaluateBelowTests {

        @Test
        @DisplayName("should trigger when candle low <= threshold and return low value")
        void shouldTriggerWhenLowBelowThreshold() {
            // Arrange: threshold 90k, candle low 89k
            Alert alert = buildAlert(AlertDirection.BELOW, "90000");
            AssetDailyValue candle = buildCandle("92000", "93000", "89000", "91000", "50000");

            // Act
            Optional<BigDecimal> result = evaluator.evaluate(alert, candle);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo(new BigDecimal("89000"));
        }

        @Test
        @DisplayName("should NOT trigger when candle low > threshold")
        void shouldNotTriggerWhenLowAboveThreshold() {
            // Arrange: threshold 90k, candle low 91k
            Alert alert = buildAlert(AlertDirection.BELOW, "90000");
            AssetDailyValue candle = buildCandle("92000", "93000", "91000", "92500", "50000");

            // Act
            Optional<BigDecimal> result = evaluator.evaluate(alert, candle);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should trigger on exact boundary (low == threshold)")
        void shouldTriggerOnExactBoundary() {
            // Arrange: threshold == low == 90000
            Alert alert = buildAlert(AlertDirection.BELOW, "90000");
            AssetDailyValue candle = buildCandle("92000", "93000", "90000", "91000", "50000");

            // Act
            Optional<BigDecimal> result = evaluator.evaluate(alert, candle);

            // Assert: <= includes equality
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo(new BigDecimal("90000"));
        }
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private Alert buildAlert(AlertDirection direction, String threshold) {
        return Alert.builder()
            .id(1L)
            .type(AlertType.PRICE_THRESHOLD)
            .direction(direction)
            .thresholdValue(new BigDecimal(threshold))
            .recurring(true)
            .active(true)
            .build();
    }

    private AssetDailyValue buildCandle(String open, String high, String low, String close, String volume) {
        return AssetDailyValue.builder()
            .id(1L)
            .open(new BigDecimal(open))
            .high(new BigDecimal(high))
            .low(new BigDecimal(low))
            .close(new BigDecimal(close))
            .volume(new BigDecimal(volume))
            .build();
    }
}
