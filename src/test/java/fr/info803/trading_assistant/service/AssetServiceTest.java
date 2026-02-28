package fr.info803.trading_assistant.service;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fr.info803.trading_assistant.dto.AssetSummaryResponse;
import fr.info803.trading_assistant.dto.CandleResponse;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetDailyValue;
import fr.info803.trading_assistant.entity.AssetSource;
import fr.info803.trading_assistant.exception.AssetNotFoundException;
import fr.info803.trading_assistant.repository.AssetDailyValueRepository;
import fr.info803.trading_assistant.repository.AssetRepository;

/**
 * Unit tests for AssetService.
 *
 * Tests the business logic for asset listing and candle retrieval in isolation (no Spring context).
 * All dependencies are mocked using Mockito.
 *
 * Covers:
 * - getAssetSummaries(): happy path, assets without candles, empty DB, alphabetical sort,
 *   2-query contract (N+1 prevention)
 * - getCandles(): happy path, unknown symbol, empty period, date window, repository call guard,
 *   OHLCV field mapping
 */
@DisplayName("AssetService Unit Tests")
@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AssetDailyValueRepository assetDailyValueRepository;

    @InjectMocks
    private AssetService assetService;

    // ── shared fixtures ──────────────────────────────────────────────────────

    private AssetSource source;
    private Asset btcAsset;
    private Asset ethAsset;

    @BeforeEach
    void setUp() {
        source = AssetSource.builder()
            .id(1L)
            .name("hyperliquid")
            .url("https://api.hyperliquid.xyz/info")
            .build();

        btcAsset = Asset.builder().id(10L).symbol("BTC").source(source).build();
        ethAsset = Asset.builder().id(11L).symbol("ETH").source(source).build();
    }

    // =========================================================================
    // getAssetSummaries()
    // =========================================================================

    @Nested
    @DisplayName("getAssetSummaries()")
    class GetAssetSummariesTests {

        @Test
        @DisplayName("should return all assets with their latest prices")
        void shouldReturnAllAssetsWithLatestPrices() {
            // Arrange
            AssetDailyValue btcCandle = buildCandle(1L, btcAsset, LocalDate.of(2026, 2, 27),
                "95000", "96500", "94000", "96000", "1234.5");
            AssetDailyValue ethCandle = buildCandle(2L, ethAsset, LocalDate.of(2026, 2, 27),
                "3100", "3200", "3050", "3150", "567.8");

            when(assetDailyValueRepository.findLatestForAllAssets()).thenReturn(List.of(btcCandle, ethCandle));
            when(assetRepository.findAll()).thenReturn(List.of(btcAsset, ethAsset));

            // Act
            List<AssetSummaryResponse> result = assetService.getAssetSummaries();

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result).extracting(AssetSummaryResponse::getSymbol).containsExactly("BTC", "ETH");

            AssetSummaryResponse btcSummary = result.get(0);
            assertThat(btcSummary.getLastPrice()).isEqualByComparingTo(new BigDecimal("96000"));
            assertThat(btcSummary.getLastDate()).isEqualTo(LocalDate.of(2026, 2, 27));

            AssetSummaryResponse ethSummary = result.get(1);
            assertThat(ethSummary.getLastPrice()).isEqualByComparingTo(new BigDecimal("3150"));
        }

        @Test
        @DisplayName("should return null prices for assets without candles")
        void shouldReturnNullPricesForAssetsWithNoCandles() {
            // Arrange: only BTC has a candle, ETH has none
            AssetDailyValue btcCandle = buildCandle(1L, btcAsset, LocalDate.of(2026, 2, 27),
                "95000", "96500", "94000", "96000", "1234.5");

            when(assetDailyValueRepository.findLatestForAllAssets()).thenReturn(List.of(btcCandle));
            when(assetRepository.findAll()).thenReturn(List.of(btcAsset, ethAsset));

            // Act
            List<AssetSummaryResponse> result = assetService.getAssetSummaries();

            // Assert
            assertThat(result).hasSize(2);

            AssetSummaryResponse btcSummary = result.stream()
                .filter(r -> r.getSymbol().equals("BTC")).findFirst().orElseThrow();
            AssetSummaryResponse ethSummary = result.stream()
                .filter(r -> r.getSymbol().equals("ETH")).findFirst().orElseThrow();

            assertThat(btcSummary.getLastPrice()).isNotNull();
            assertThat(btcSummary.getLastDate()).isNotNull();

            // ETH has no candle → fields must be null (not absent, not zero)
            assertThat(ethSummary.getLastPrice()).isNull();
            assertThat(ethSummary.getLastDate()).isNull();
        }

        @Test
        @DisplayName("should return empty list when no assets exist")
        void shouldReturnEmptyListWhenNoAssetsExist() {
            // Arrange
            when(assetDailyValueRepository.findLatestForAllAssets()).thenReturn(Collections.emptyList());
            when(assetRepository.findAll()).thenReturn(Collections.emptyList());

            // Act
            List<AssetSummaryResponse> result = assetService.getAssetSummaries();

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should sort results alphabetically by symbol")
        void shouldSortResultsAlphabeticallyBySymbol() {
            // Arrange: unsorted input — service must sort
            Asset mantaAsset = Asset.builder().id(12L).symbol("MANTA").source(source).build();
            Asset aeroAsset  = Asset.builder().id(13L).symbol("AERO").source(source).build();

            when(assetDailyValueRepository.findLatestForAllAssets()).thenReturn(Collections.emptyList());
            when(assetRepository.findAll()).thenReturn(List.of(mantaAsset, btcAsset, aeroAsset, ethAsset));

            // Act
            List<AssetSummaryResponse> result = assetService.getAssetSummaries();

            // Assert
            assertThat(result).extracting(AssetSummaryResponse::getSymbol)
                .containsExactly("AERO", "BTC", "ETH", "MANTA");
        }

        @Test
        @DisplayName("should call both repositories exactly once (2-query contract, no N+1)")
        void shouldCallBothRepositoriesExactlyOnce() {
            // Arrange
            when(assetDailyValueRepository.findLatestForAllAssets()).thenReturn(Collections.emptyList());
            when(assetRepository.findAll()).thenReturn(Collections.emptyList());

            // Act
            assetService.getAssetSummaries();

            // Assert: exactly one call to each repository regardless of asset count
            verify(assetDailyValueRepository).findLatestForAllAssets();
            verify(assetRepository).findAll();
        }
    }

    // =========================================================================
    // getCandles()
    // =========================================================================

    @Nested
    @DisplayName("getCandles()")
    class GetCandlesTests {

        @Test
        @DisplayName("should return candles for a known symbol")
        void shouldReturnCandlesForKnownSymbol() {
            // Arrange
            LocalDate date1 = LocalDate.now().minusDays(5);
            LocalDate date2 = LocalDate.now().minusDays(4);
            AssetDailyValue candle1 = buildCandle(1L, btcAsset, date1,
                "95000", "96500", "94000", "96000", "1234.5");
            AssetDailyValue candle2 = buildCandle(2L, btcAsset, date2,
                "96000", "97000", "95500", "96800", "2345.6");

            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(assetDailyValueRepository.findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                eq(btcAsset), any(LocalDate.class)
            )).thenReturn(List.of(candle1, candle2));

            // Act
            List<CandleResponse> result = assetService.getCandles("BTC");

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getDate()).isEqualTo(date1);
            assertThat(result.get(1).getDate()).isEqualTo(date2);
        }

        @Test
        @DisplayName("should throw AssetNotFoundException for unknown symbol")
        void shouldThrowAssetNotFoundExceptionForUnknownSymbol() {
            // Arrange
            when(assetRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> assetService.getCandles("UNKNOWN"))
                .isInstanceOf(AssetNotFoundException.class)
                .hasMessageContaining("UNKNOWN");
        }

        @Test
        @DisplayName("should return empty list when no candles exist in the period")
        void shouldReturnEmptyListWhenNoCandlesInPeriod() {
            // Arrange
            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(assetDailyValueRepository.findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                any(), any()
            )).thenReturn(Collections.emptyList());

            // Act
            List<CandleResponse> result = assetService.getCandles("BTC");

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should query candles starting from exactly one year ago")
        void shouldQueryCandlesFromExactlyOneYearAgo() {
            // Arrange
            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(assetDailyValueRepository.findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                any(), any()
            )).thenReturn(Collections.emptyList());

            // Act
            assetService.getCandles("BTC");

            // Assert: capture the fromDate argument and verify it is exactly LocalDate.now().minusYears(1)
            LocalDate expectedFromDate = LocalDate.now().minusYears(1);
            ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
            verify(assetDailyValueRepository).findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                eq(btcAsset), dateCaptor.capture()
            );
            assertThat(dateCaptor.getValue()).isEqualTo(expectedFromDate);
        }

        @Test
        @DisplayName("should not call candle repository when asset is not found")
        void shouldNotCallCandleRepositoryWhenAssetNotFound() {
            // Arrange
            when(assetRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());

            // Act
            try {
                assetService.getCandles("UNKNOWN");
            } catch (AssetNotFoundException ignored) { }

            // Assert: the candle repository must never be consulted if the asset doesn't exist
            verify(assetDailyValueRepository, never())
                .findByAssetAndDateGreaterThanEqualOrderByDateAsc(any(), any());
        }

        @Test
        @DisplayName("should map all OHLCV fields correctly to CandleResponse")
        void shouldMapAllOhlcvFieldsCorrectly() {
            // Arrange: candle with distinct values for each field to detect any swap in mapping
            LocalDate date = LocalDate.of(2026, 1, 15);
            AssetDailyValue candle = buildCandle(1L, btcAsset, date,
                "95000.1234", "96500.5678", "94000.9012", "96000.3456", "28345.7890");

            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(assetDailyValueRepository.findByAssetAndDateGreaterThanEqualOrderByDateAsc(
                any(), any()
            )).thenReturn(List.of(candle));

            // Act
            List<CandleResponse> result = assetService.getCandles("BTC");

            // Assert: every OHLCV field is correctly mapped to its counterpart in CandleResponse
            assertThat(result).hasSize(1);
            CandleResponse response = result.get(0);
            assertThat(response.getDate()).isEqualTo(date);
            assertThat(response.getOpen()).isEqualByComparingTo(new BigDecimal("95000.1234"));
            assertThat(response.getHigh()).isEqualByComparingTo(new BigDecimal("96500.5678"));
            assertThat(response.getLow()).isEqualByComparingTo(new BigDecimal("94000.9012"));
            assertThat(response.getClose()).isEqualByComparingTo(new BigDecimal("96000.3456"));
            assertThat(response.getVolume()).isEqualByComparingTo(new BigDecimal("28345.7890"));
        }
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private AssetDailyValue buildCandle(Long id, Asset asset, LocalDate date,
            String open, String high, String low, String close, String volume) {
        return AssetDailyValue.builder()
            .id(id)
            .asset(asset)
            .date(date)
            .open(new BigDecimal(open))
            .high(new BigDecimal(high))
            .low(new BigDecimal(low))
            .close(new BigDecimal(close))
            .volume(new BigDecimal(volume))
            .build();
    }
}
