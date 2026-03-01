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
 * Unit tests for VolumeThresholdEvaluator.
 *
 * Tests the Strategy Pattern implementation for VOLUME_THRESHOLD alerts.
 * No mocks needed — VolumeThresholdEvaluator is a pure function with no dependencies.
 *
 * Covers:
 * - supports() returns true only for VOLUME_THRESHOLD
 * - ABOVE direction: triggers when volume >= threshold, returns volume value
 * - ABOVE direction: does not trigger when volume < threshold
 * - ABOVE direction: triggers on exact boundary (volume == threshold)
 * - BELOW direction: triggers when volume <= threshold, returns volume value
 * - BELOW direction: does not trigger when volume > threshold
 * - BELOW direction: triggers on exact boundary (volume == threshold)
 */
@DisplayName("VolumeThresholdEvaluator Unit Tests")
class VolumeThresholdEvaluatorTest {

    private VolumeThresholdEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new VolumeThresholdEvaluator();
    }

    // =========================================================================
    // supports()
    // =========================================================================

    @Nested
    @DisplayName("supports()")
    class SupportsTests {

        @Test
        @DisplayName("should return true for VOLUME_THRESHOLD")
        void shouldReturnTrueForVolumeThreshold() {
            assertThat(evaluator.supports(AlertType.VOLUME_THRESHOLD)).isTrue();
        }

        @Test
        @DisplayName("should return false for PRICE_THRESHOLD")
        void shouldReturnFalseForPriceThreshold() {
            assertThat(evaluator.supports(AlertType.PRICE_THRESHOLD)).isFalse();
        }
    }

    // =========================================================================
    // evaluate() — ABOVE direction
    // =========================================================================

    @Nested
    @DisplayName("evaluate() — ABOVE direction")
    class EvaluateAboveTests {

        @Test
        @DisplayName("should trigger when volume >= threshold and return volume value")
        void shouldTriggerWhenVolumeAboveThreshold() {
            // Arrange: threshold 50000, volume 75000
            Alert alert = buildAlert(AlertDirection.ABOVE, "50000");
            AssetDailyValue candle = buildCandle("75000");

            // Act
            Optional<BigDecimal> result = evaluator.evaluate(alert, candle);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo(new BigDecimal("75000"));
        }

        @Test
        @DisplayName("should NOT trigger when volume < threshold")
        void shouldNotTriggerWhenVolumeBelowThreshold() {
            // Arrange: threshold 50000, volume 30000
            Alert alert = buildAlert(AlertDirection.ABOVE, "50000");
            AssetDailyValue candle = buildCandle("30000");

            // Act
            Optional<BigDecimal> result = evaluator.evaluate(alert, candle);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should trigger on exact boundary (volume == threshold)")
        void shouldTriggerOnExactBoundary() {
            // Arrange: threshold == volume == 50000
            Alert alert = buildAlert(AlertDirection.ABOVE, "50000");
            AssetDailyValue candle = buildCandle("50000");

            // Act
            Optional<BigDecimal> result = evaluator.evaluate(alert, candle);

            // Assert: >= includes equality
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo(new BigDecimal("50000"));
        }
    }

    // =========================================================================
    // evaluate() — BELOW direction
    // =========================================================================

    @Nested
    @DisplayName("evaluate() — BELOW direction")
    class EvaluateBelowTests {

        @Test
        @DisplayName("should trigger when volume <= threshold and return volume value")
        void shouldTriggerWhenVolumeBelowThreshold() {
            // Arrange: threshold 10000, volume 5000
            Alert alert = buildAlert(AlertDirection.BELOW, "10000");
            AssetDailyValue candle = buildCandle("5000");

            // Act
            Optional<BigDecimal> result = evaluator.evaluate(alert, candle);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo(new BigDecimal("5000"));
        }

        @Test
        @DisplayName("should NOT trigger when volume > threshold")
        void shouldNotTriggerWhenVolumeAboveThreshold() {
            // Arrange: threshold 10000, volume 15000
            Alert alert = buildAlert(AlertDirection.BELOW, "10000");
            AssetDailyValue candle = buildCandle("15000");

            // Act
            Optional<BigDecimal> result = evaluator.evaluate(alert, candle);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should trigger on exact boundary (volume == threshold)")
        void shouldTriggerOnExactBoundary() {
            // Arrange: threshold == volume == 10000
            Alert alert = buildAlert(AlertDirection.BELOW, "10000");
            AssetDailyValue candle = buildCandle("10000");

            // Act
            Optional<BigDecimal> result = evaluator.evaluate(alert, candle);

            // Assert: <= includes equality
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo(new BigDecimal("10000"));
        }
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private Alert buildAlert(AlertDirection direction, String threshold) {
        return Alert.builder()
            .id(1L)
            .type(AlertType.VOLUME_THRESHOLD)
            .direction(direction)
            .thresholdValue(new BigDecimal(threshold))
            .recurring(true)
            .active(true)
            .build();
    }

    private AssetDailyValue buildCandle(String volume) {
        return AssetDailyValue.builder()
            .id(1L)
            .open(new BigDecimal("95000"))
            .high(new BigDecimal("96000"))
            .low(new BigDecimal("94000"))
            .close(new BigDecimal("95500"))
            .volume(new BigDecimal(volume))
            .build();
    }
}
