package fr.info803.trading_assistant.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fr.info803.trading_assistant.dto.MovingAveragePointResponse;
import fr.info803.trading_assistant.dto.MovingAverageSeriesResponse;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetDailyValue;
import fr.info803.trading_assistant.entity.AssetSource;
import fr.info803.trading_assistant.exception.AssetNotFoundException;
import fr.info803.trading_assistant.exception.InvalidMovingAverageRequestException;
import fr.info803.trading_assistant.repository.AssetDailyValueRepository;
import fr.info803.trading_assistant.repository.AssetRepository;

/**
 * Unit tests for MovingAverageService.
 *
 * Tests the SMA and EMA calculation logic in isolation (no Spring context).
 * All dependencies (repositories) are mocked using Mockito.
 *
 * Covers:
 * - SMA computation: happy path, insufficient data, exact period, sliding window correctness
 * - EMA computation: happy path, seeding from SMA, exponential decay formula
 * - Validation: invalid type, empty periods, negative period, null period
 * - Integration: unknown symbol, multiple periods in one call, candle repository not called on error
 */
@DisplayName("MovingAverageService Unit Tests")
@ExtendWith(MockitoExtension.class)
class MovingAverageServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetDailyValueRepository assetDailyValueRepository;

    @InjectMocks
    private MovingAverageService movingAverageService;

    // ── shared fixtures ──────────────────────────────────────────────────────

    private AssetSource source;
    private Asset btcAsset;

    @BeforeEach
    void setUp() {
        source = AssetSource.builder()
            .id(1L)
            .name("hyperliquid")
            .url("https://api.hyperliquid.xyz/info")
            .build();

        btcAsset = Asset.builder().id(10L).symbol("BTC").source(source).build();
    }

    // =========================================================================
    // SMA computation
    // =========================================================================

    @Nested
    @DisplayName("SMA computation")
    class SmaTests {

        @Test
        @DisplayName("should compute SMA correctly with sufficient data")
        void shouldComputeSmaCorrectly() {
            /*
                Candles close: 10, 20, 30, 40, 50
                SMA-3:
                  day3 = (10 + 20 + 30) / 3 = 20.0
                  day4 = (20 + 30 + 40) / 3 = 30.0
                  day5 = (30 + 40 + 50) / 3 = 40.0
            */
            List<AssetDailyValue> candles = buildCandles(10, 20, 30, 40, 50);

            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(assetDailyValueRepository.findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                eq(btcAsset), any(LocalDate.class)
            )).thenReturn(candles);

            // Act
            List<MovingAverageSeriesResponse> result =
                movingAverageService.getMovingAverages("BTC", "SMA", List.of(3));

            // Assert
            assertThat(result).hasSize(1);
            MovingAverageSeriesResponse series = result.get(0);
            assertThat(series.getType()).isEqualTo("SMA");
            assertThat(series.getPeriod()).isEqualTo(3);
            assertThat(series.getValues()).hasSize(3);

            assertThat(series.getValues().get(0).getValue())
                .isEqualByComparingTo(new BigDecimal("20"));
            assertThat(series.getValues().get(1).getValue())
                .isEqualByComparingTo(new BigDecimal("30"));
            assertThat(series.getValues().get(2).getValue())
                .isEqualByComparingTo(new BigDecimal("40"));
        }

        @Test
        @DisplayName("should return empty values when not enough data for SMA period")
        void shouldReturnEmptyWhenInsufficientData() {
            // 2 candles but period is 5 → not enough data
            List<AssetDailyValue> candles = buildCandles(10, 20);

            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(assetDailyValueRepository.findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                eq(btcAsset), any(LocalDate.class)
            )).thenReturn(candles);

            // Act
            List<MovingAverageSeriesResponse> result =
                movingAverageService.getMovingAverages("BTC", "SMA", List.of(5));

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getValues()).isEmpty();
        }

        @Test
        @DisplayName("should return single point when data count equals period exactly")
        void shouldReturnSinglePointWhenDataEqualsperiod() {
            // 3 candles, period 3 → exactly 1 SMA point
            List<AssetDailyValue> candles = buildCandles(100, 200, 300);

            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(assetDailyValueRepository.findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                eq(btcAsset), any(LocalDate.class)
            )).thenReturn(candles);

            // Act
            List<MovingAverageSeriesResponse> result =
                movingAverageService.getMovingAverages("BTC", "SMA", List.of(3));

            // Assert
            assertThat(result.get(0).getValues()).hasSize(1);
            assertThat(result.get(0).getValues().get(0).getValue())
                .isEqualByComparingTo(new BigDecimal("200")); // (100+200+300)/3
        }

        @Test
        @DisplayName("should compute SMA-1 equal to the close price itself")
        void shouldComputeSma1AsClosePriceItself() {
            // SMA-1 = close of each day
            List<AssetDailyValue> candles = buildCandles(42, 99, 7);

            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(assetDailyValueRepository.findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                eq(btcAsset), any(LocalDate.class)
            )).thenReturn(candles);

            // Act
            List<MovingAverageSeriesResponse> result =
                movingAverageService.getMovingAverages("BTC", "SMA", List.of(1));

            // Assert
            assertThat(result.get(0).getValues()).hasSize(3);
            assertThat(result.get(0).getValues().get(0).getValue())
                .isEqualByComparingTo(new BigDecimal("42"));
            assertThat(result.get(0).getValues().get(1).getValue())
                .isEqualByComparingTo(new BigDecimal("99"));
            assertThat(result.get(0).getValues().get(2).getValue())
                .isEqualByComparingTo(new BigDecimal("7"));
        }

        @Test
        @DisplayName("should assign correct dates to SMA points")
        void shouldAssignCorrectDatesToSmaPoints() {
            List<AssetDailyValue> candles = buildCandles(10, 20, 30, 40);

            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(assetDailyValueRepository.findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                eq(btcAsset), any(LocalDate.class)
            )).thenReturn(candles);

            // Act
            List<MovingAverageSeriesResponse> result =
                movingAverageService.getMovingAverages("BTC", "SMA", List.of(3));

            // Assert: SMA-3 starts at candle index 2 (3rd candle)
            assertThat(result.get(0).getValues().get(0).getDate())
                .isEqualTo(candles.get(2).getDate());
            assertThat(result.get(0).getValues().get(1).getDate())
                .isEqualTo(candles.get(3).getDate());
        }
    }

    // =========================================================================
    // EMA computation
    // =========================================================================

    @Nested
    @DisplayName("EMA computation")
    class EmaTests {

        @Test
        @DisplayName("should compute EMA correctly with exponential decay")
        void shouldComputeEmaCorrectly() {
            /*
                Candles close: 10, 20, 30, 40, 50
                EMA-3 (k = 2/(3+1) = 0.5):
                  seed = SMA(10,20,30) = 20.0
                  day4 = 40 * 0.5 + 20 * 0.5 = 30.0
                  day5 = 50 * 0.5 + 30 * 0.5 = 40.0
            */
            List<AssetDailyValue> candles = buildCandles(10, 20, 30, 40, 50);

            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(assetDailyValueRepository.findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                eq(btcAsset), any(LocalDate.class)
            )).thenReturn(candles);

            // Act
            List<MovingAverageSeriesResponse> result =
                movingAverageService.getMovingAverages("BTC", "EMA", List.of(3));

            // Assert
            assertThat(result).hasSize(1);
            MovingAverageSeriesResponse series = result.get(0);
            assertThat(series.getType()).isEqualTo("EMA");
            assertThat(series.getPeriod()).isEqualTo(3);
            assertThat(series.getValues()).hasSize(3);

            // Seed = SMA = 20
            assertThat(series.getValues().get(0).getValue())
                .isEqualByComparingTo(new BigDecimal("20"));
            // 40 * 0.5 + 20 * 0.5 = 30
            assertThat(series.getValues().get(1).getValue())
                .isEqualByComparingTo(new BigDecimal("30"));
            // 50 * 0.5 + 30 * 0.5 = 40
            assertThat(series.getValues().get(2).getValue())
                .isEqualByComparingTo(new BigDecimal("40"));
        }

        @Test
        @DisplayName("should return empty values when not enough data for EMA period")
        void shouldReturnEmptyWhenInsufficientDataForEma() {
            List<AssetDailyValue> candles = buildCandles(10, 20);

            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(assetDailyValueRepository.findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                eq(btcAsset), any(LocalDate.class)
            )).thenReturn(candles);

            // Act
            List<MovingAverageSeriesResponse> result =
                movingAverageService.getMovingAverages("BTC", "EMA", List.of(5));

            // Assert
            assertThat(result.get(0).getValues()).isEmpty();
        }

        @Test
        @DisplayName("should seed EMA with SMA of first N close prices")
        void shouldSeedEmaWithSmaOfFirstNPrices() {
            /*
                Candles close: 100, 200, 300
                EMA-3 seed = SMA(100, 200, 300) = 200
                With exactly period candles, we only get the seed point.
            */
            List<AssetDailyValue> candles = buildCandles(100, 200, 300);

            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(assetDailyValueRepository.findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                eq(btcAsset), any(LocalDate.class)
            )).thenReturn(candles);

            // Act
            List<MovingAverageSeriesResponse> result =
                movingAverageService.getMovingAverages("BTC", "EMA", List.of(3));

            // Assert: first EMA = SMA seed
            assertThat(result.get(0).getValues()).hasSize(1);
            assertThat(result.get(0).getValues().get(0).getValue())
                .isEqualByComparingTo(new BigDecimal("200"));
        }

        @Test
        @DisplayName("EMA should give more weight to recent prices than SMA")
        void emaShouldGiveMoreWeightToRecentPrices() {
            /*
                Candles close: 10, 10, 10, 10, 100 (spike on last day)
                SMA-4 for last point: (10+10+10+100)/4 = 32.5
                EMA-4 for last point: will be higher than SMA because EMA
                weights recent prices more. k = 2/5 = 0.4

                Seed = SMA(10,10,10,10) = 10
                EMA(day5) = 100 * 0.4 + 10 * 0.6 = 46.0

                EMA(46) > SMA(32.5) → confirms EMA reacts faster.
            */
            List<AssetDailyValue> candles = buildCandles(10, 10, 10, 10, 100);

            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(assetDailyValueRepository.findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                eq(btcAsset), any(LocalDate.class)
            )).thenReturn(candles);

            // Act
            List<MovingAverageSeriesResponse> smaResult =
                movingAverageService.getMovingAverages("BTC", "SMA", List.of(4));
            List<MovingAverageSeriesResponse> emaResult =
                movingAverageService.getMovingAverages("BTC", "EMA", List.of(4));

            // Assert
            List<MovingAveragePointResponse> smaValues = smaResult.get(0).getValues();
            List<MovingAveragePointResponse> emaValues = emaResult.get(0).getValues();

            // Get the last point for each
            BigDecimal lastSma = smaValues.get(smaValues.size() - 1).getValue();
            BigDecimal lastEma = emaValues.get(emaValues.size() - 1).getValue();

            assertThat(lastEma).isGreaterThan(lastSma);
        }
    }

    // =========================================================================
    // Validation
    // =========================================================================

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("should throw InvalidMovingAverageRequestException for unknown type")
        void shouldThrowForUnknownType() {
            assertThatThrownBy(() ->
                movingAverageService.getMovingAverages("BTC", "VWAP", List.of(20)))
                .isInstanceOf(InvalidMovingAverageRequestException.class)
                .hasMessageContaining("VWAP");
        }

        @Test
        @DisplayName("should throw InvalidMovingAverageRequestException for empty periods")
        void shouldThrowForEmptyPeriods() {
            assertThatThrownBy(() ->
                movingAverageService.getMovingAverages("BTC", "SMA", List.of()))
                .isInstanceOf(InvalidMovingAverageRequestException.class)
                .hasMessageContaining("period");
        }

        @Test
        @DisplayName("should throw InvalidMovingAverageRequestException for null periods list")
        void shouldThrowForNullPeriodsList() {
            assertThatThrownBy(() ->
                movingAverageService.getMovingAverages("BTC", "SMA", null))
                .isInstanceOf(InvalidMovingAverageRequestException.class);
        }

        @Test
        @DisplayName("should throw InvalidMovingAverageRequestException for negative period")
        void shouldThrowForNegativePeriod() {
            assertThatThrownBy(() ->
                movingAverageService.getMovingAverages("BTC", "SMA", List.of(-5)))
                .isInstanceOf(InvalidMovingAverageRequestException.class)
                .hasMessageContaining("-5");
        }

        @Test
        @DisplayName("should throw InvalidMovingAverageRequestException for zero period")
        void shouldThrowForZeroPeriod() {
            assertThatThrownBy(() ->
                movingAverageService.getMovingAverages("BTC", "SMA", List.of(0)))
                .isInstanceOf(InvalidMovingAverageRequestException.class)
                .hasMessageContaining("0");
        }

        @Test
        @DisplayName("should accept type in any case (case-insensitive)")
        void shouldAcceptTypeCaseInsensitive() {
            List<AssetDailyValue> candles = buildCandles(10, 20, 30);

            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(assetDailyValueRepository.findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                eq(btcAsset), any(LocalDate.class)
            )).thenReturn(candles);

            // Act: lowercase type
            List<MovingAverageSeriesResponse> result =
                movingAverageService.getMovingAverages("BTC", "sma", List.of(2));

            // Assert: type is normalized to uppercase in response
            assertThat(result.get(0).getType()).isEqualTo("SMA");
        }

        @Test
        @DisplayName("should not call repositories when type is invalid")
        void shouldNotCallRepositoriesWhenTypeInvalid() {
            try {
                movingAverageService.getMovingAverages("BTC", "INVALID", List.of(20));
            } catch (InvalidMovingAverageRequestException ignored) { }

            verify(assetRepository, never()).findBySymbol(any());
            verify(assetDailyValueRepository, never())
                .findByAssetAndDateGreaterThanEqualOrderByDateAsc(any(), any());
        }
    }

    // =========================================================================
    // Asset resolution
    // =========================================================================

    @Nested
    @DisplayName("Asset resolution")
    class AssetResolutionTests {

        @Test
        @DisplayName("should throw AssetNotFoundException for unknown symbol")
        void shouldThrowAssetNotFoundForUnknownSymbol() {
            when(assetRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                movingAverageService.getMovingAverages("UNKNOWN", "SMA", List.of(20)))
                .isInstanceOf(AssetNotFoundException.class)
                .hasMessageContaining("UNKNOWN");
        }

        @Test
        @DisplayName("should not call candle repository when asset is not found")
        void shouldNotCallCandleRepositoryWhenAssetNotFound() {
            when(assetRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());

            try {
                movingAverageService.getMovingAverages("UNKNOWN", "SMA", List.of(20));
            } catch (AssetNotFoundException ignored) { }

            verify(assetDailyValueRepository, never())
                .findByAssetAndDateGreaterThanEqualOrderByDateAsc(any(), any());
        }
    }

    // =========================================================================
    // Multiple periods
    // =========================================================================

    @Nested
    @DisplayName("Multiple periods")
    class MultiplePeriodsTests {

        @Test
        @DisplayName("should return one series per requested period")
        void shouldReturnOneSeriesPerPeriod() {
            List<AssetDailyValue> candles = buildCandles(10, 20, 30, 40, 50);

            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(assetDailyValueRepository.findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                eq(btcAsset), any(LocalDate.class)
            )).thenReturn(candles);

            // Act: request SMA with periods 2 and 3
            List<MovingAverageSeriesResponse> result =
                movingAverageService.getMovingAverages("BTC", "SMA", List.of(2, 3));

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getPeriod()).isEqualTo(2);
            assertThat(result.get(1).getPeriod()).isEqualTo(3);

            // SMA-2 produces 4 points (indices 1-4), SMA-3 produces 3 points (indices 2-4)
            assertThat(result.get(0).getValues()).hasSize(4);
            assertThat(result.get(1).getValues()).hasSize(3);
        }

        @Test
        @DisplayName("should handle mix of computable and non-computable periods")
        void shouldHandleMixOfComputableAndNonComputablePeriods() {
            // 3 candles → SMA-2 computable (2 points), SMA-10 not computable (empty)
            List<AssetDailyValue> candles = buildCandles(10, 20, 30);

            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(assetDailyValueRepository.findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                eq(btcAsset), any(LocalDate.class)
            )).thenReturn(candles);

            // Act
            List<MovingAverageSeriesResponse> result =
                movingAverageService.getMovingAverages("BTC", "SMA", List.of(2, 10));

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getValues()).hasSize(2); // SMA-2 computable
            assertThat(result.get(1).getValues()).isEmpty();    // SMA-10 not computable
        }
    }

    // =========================================================================
    // helpers
    // =========================================================================

    /*
        Builds a list of AssetDailyValue with sequential dates and the given close prices.
        All other OHLCV fields are set to the close price for simplicity — only close
        matters for MA calculations.
    */
    private List<AssetDailyValue> buildCandles(int... closePrices) {
        List<AssetDailyValue> candles = new ArrayList<>();
        LocalDate baseDate = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < closePrices.length; i++) {
            BigDecimal price = new BigDecimal(closePrices[i]);
            candles.add(AssetDailyValue.builder()
                .id((long) (i + 1))
                .asset(btcAsset)
                .date(baseDate.plusDays(i))
                .open(price)
                .high(price)
                .low(price)
                .close(price)
                .volume(BigDecimal.ONE)
                .build());
        }
        return candles;
    }
}
