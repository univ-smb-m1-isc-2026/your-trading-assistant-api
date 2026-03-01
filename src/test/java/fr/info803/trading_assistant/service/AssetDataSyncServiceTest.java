package fr.info803.trading_assistant.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import fr.info803.trading_assistant.dto.DailyValueDto;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetDailyValue;
import fr.info803.trading_assistant.entity.AssetSource;
import fr.info803.trading_assistant.repository.AssetDailyValueRepository;
import fr.info803.trading_assistant.repository.AssetRepository;
import fr.info803.trading_assistant.repository.AssetSourceRepository;

/**
 * Unit tests for AssetDataSyncService.
 *
 * Tests the orchestration logic (routing sources to providers, upsert behaviour)
 * in isolation — all repositories and providers are mocked. No Spring context.
 *
 * Why Mockito.spy() on the service itself?
 *   The public syncForDate() calls the package-private upsertDailyValue() method.
 *   When we want to verify that upsert is called without actually hitting the DB,
 *   we spy on the service and stub upsertDailyValue() to do nothing.
 *   This lets us verify the orchestration (routing, iteration) without coupling
 *   tests to the upsert implementation details.
 *
 * Covers:
 * - syncForDate() skips entirely when no AssetSource exists in DB
 * - syncForDate() skips a source that has no registered provider
 * - syncForDate() calls upsertDailyValue() once per returned DTO
 * - syncForDate() counts correctly when the provider returns an empty list
 * - syncForDate() continues with remaining assets when one throws
 * - syncForDateRange() calls provider with the full date range and upserts all candles
 * - syncForDateRange() skips when no AssetSource exists
 * - syncForDateRange() continues with remaining assets when one throws
 * - upsertDailyValue() performs INSERT when (asset, date) is not yet in DB
 * - upsertDailyValue() performs UPDATE when (asset, date) already exists
 */
@DisplayName("AssetDataSyncService Unit Tests")
class AssetDataSyncServiceTest {

    // ── mocked collaborators ─────────────────────────────────────────────────
    private AssetSourceRepository assetSourceRepository;
    private AssetRepository assetRepository;
    private AssetDailyValueRepository assetDailyValueRepository;
    private AssetDataProvider hyperliquidProvider;
    private AlertService alertService;

    // service under test
    private AssetDataSyncService service;

    // ── shared fixtures ──────────────────────────────────────────────────────
    private static final LocalDate TEST_DATE = LocalDate.of(2025, 1, 15);

    private AssetSource hyperliquidSource;
    private Asset btcAsset;

    @BeforeEach
    void setUp() {
        assetSourceRepository = mock(AssetSourceRepository.class);
        assetRepository = mock(AssetRepository.class);
        assetDailyValueRepository = mock(AssetDailyValueRepository.class);
        hyperliquidProvider = mock(AssetDataProvider.class);
        alertService = mock(AlertService.class);

        // Provider reports its name so the service can match it to the DB source
        when(hyperliquidProvider.getSourceName()).thenReturn("hyperliquid");

        service = new AssetDataSyncService(
            assetSourceRepository,
            assetRepository,
            assetDailyValueRepository,
            alertService,
            List.of(hyperliquidProvider)
        );

        // Reusable entity fixtures
        hyperliquidSource = AssetSource.builder()
            .id(1L)
            .name("hyperliquid")
            .url("https://api.hyperliquid.xyz/info")
            .build();

        btcAsset = Asset.builder()
            .id(10L)
            .symbol("BTC")
            .source(hyperliquidSource)
            .build();
    }

    // =========================================================================
    // syncForDate() — orchestration tests
    // =========================================================================

    @Nested
    @DisplayName("syncForDate() — orchestration")
    class SyncForDateTests {

        @Test
        @DisplayName("should do nothing when no AssetSource exists in DB")
        void shouldSkipWhenNoSources() {
            // Arrange
            when(assetSourceRepository.findAll()).thenReturn(Collections.emptyList());

            // Act
            service.syncForDate(TEST_DATE);

            // Assert: provider and asset repository were never consulted
            verify(assetRepository, never()).findBySource(any());
            verify(hyperliquidProvider, never()).fetchDailyValues(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should skip source when no provider is registered for it")
        void shouldSkipSourceWithUnknownProvider() {
            // Arrange: source whose name does not match any provider
            AssetSource unknownSource = AssetSource.builder()
                .id(2L)
                .name("alphavantage")
                .url("https://www.alphavantage.co/query")
                .build();

            when(assetSourceRepository.findAll()).thenReturn(List.of(unknownSource));

            // Act
            service.syncForDate(TEST_DATE);

            // Assert: assets are never fetched for this source
            verify(assetRepository, never()).findBySource(any());
            verify(hyperliquidProvider, never()).fetchDailyValues(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should call upsertDailyValue once per DTO returned by provider")
        void shouldUpsertForEachReturnedDto() {
            // Arrange
            DailyValueDto dto = buildDto(TEST_DATE);

            when(assetSourceRepository.findAll()).thenReturn(List.of(hyperliquidSource));
            when(assetRepository.findBySource(hyperliquidSource)).thenReturn(List.of(btcAsset));
            when(hyperliquidProvider.fetchDailyValues(
                eq("BTC"), eq(TEST_DATE), eq(TEST_DATE), eq("https://api.hyperliquid.xyz/info")
            )).thenReturn(List.of(dto));

            // Spy so we can stub upsertDailyValue without hitting the DB
            AssetDataSyncService spied = spy(service);
            doNothing().when(spied).upsertDailyValue(any(), any());

            // Act
            spied.syncForDate(TEST_DATE);

            // Assert: upsert called exactly once (one DTO returned)
            verify(spied, times(1)).upsertDailyValue(eq(btcAsset), eq(dto));
        }

        @Test
        @DisplayName("should NOT call upsertDailyValue when provider returns empty list")
        void shouldNotUpsertWhenProviderReturnsEmpty() {
            // Arrange
            when(assetSourceRepository.findAll()).thenReturn(List.of(hyperliquidSource));
            when(assetRepository.findBySource(hyperliquidSource)).thenReturn(List.of(btcAsset));
            when(hyperliquidProvider.fetchDailyValues(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

            AssetDataSyncService spied = spy(service);
            doNothing().when(spied).upsertDailyValue(any(), any());

            // Act
            spied.syncForDate(TEST_DATE);

            // Assert
            verify(spied, never()).upsertDailyValue(any(), any());
        }

        @Test
        @DisplayName("should upsert for multiple assets from the same source")
        void shouldUpsertForMultipleAssetsFromSameSource() {
            // Arrange
            Asset ethAsset = Asset.builder().id(11L).symbol("ETH").source(hyperliquidSource).build();
            DailyValueDto btcDto = buildDto(TEST_DATE);
            DailyValueDto ethDto = buildDto(TEST_DATE);

            when(assetSourceRepository.findAll()).thenReturn(List.of(hyperliquidSource));
            when(assetRepository.findBySource(hyperliquidSource))
                .thenReturn(List.of(btcAsset, ethAsset));
            when(hyperliquidProvider.fetchDailyValues(eq("BTC"), any(), any(), any()))
                .thenReturn(List.of(btcDto));
            when(hyperliquidProvider.fetchDailyValues(eq("ETH"), any(), any(), any()))
                .thenReturn(List.of(ethDto));

            AssetDataSyncService spied = spy(service);
            doNothing().when(spied).upsertDailyValue(any(), any());

            // Act
            spied.syncForDate(TEST_DATE);

            // Assert: one upsert per asset
            verify(spied, times(1)).upsertDailyValue(eq(btcAsset), eq(btcDto));
            verify(spied, times(1)).upsertDailyValue(eq(ethAsset), eq(ethDto));
        }

        @Test
        @DisplayName("should continue syncing remaining assets when one throws an exception")
        void shouldContinueWhenOneAssetThrows() {
            // Arrange
            Asset ethAsset = Asset.builder().id(11L).symbol("ETH").source(hyperliquidSource).build();
            DailyValueDto ethDto = buildDto(TEST_DATE);

            when(assetSourceRepository.findAll()).thenReturn(List.of(hyperliquidSource));
            when(assetRepository.findBySource(hyperliquidSource))
                .thenReturn(List.of(btcAsset, ethAsset));

            // BTC throws, ETH succeeds
            when(hyperliquidProvider.fetchDailyValues(eq("BTC"), any(), any(), any()))
                .thenThrow(new RuntimeException("network error"));
            when(hyperliquidProvider.fetchDailyValues(eq("ETH"), any(), any(), any()))
                .thenReturn(List.of(ethDto));

            AssetDataSyncService spied = spy(service);
            doNothing().when(spied).upsertDailyValue(any(), any());

            // Act — must not throw
            spied.syncForDate(TEST_DATE);

            // Assert: ETH was still synced despite BTC failure
            verify(spied, never()).upsertDailyValue(eq(btcAsset), any());
            verify(spied, times(1)).upsertDailyValue(eq(ethAsset), eq(ethDto));
        }
    }

    // =========================================================================
    // syncForDateRange() — bulk orchestration tests
    // =========================================================================

    @Nested
    @DisplayName("syncForDateRange() — bulk orchestration")
    class SyncForDateRangeTests {

        private static final LocalDate RANGE_START = LocalDate.of(2024, 1, 15);
        private static final LocalDate RANGE_END = LocalDate.of(2025, 1, 14);

        @Test
        @DisplayName("should do nothing when no AssetSource exists in DB")
        void shouldSkipWhenNoSources() {
            // Arrange
            when(assetSourceRepository.findAll()).thenReturn(Collections.emptyList());

            // Act
            service.syncForDateRange(RANGE_START, RANGE_END);

            // Assert
            verify(assetRepository, never()).findBySource(any());
            verify(hyperliquidProvider, never()).fetchDailyValues(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should call provider with the full date range and upsert all returned candles")
        void shouldFetchRangeAndUpsertAllCandles() {
            // Arrange: provider returns 3 candles for the range
            DailyValueDto dto1 = buildDto(RANGE_START);
            DailyValueDto dto2 = buildDto(RANGE_START.plusDays(1));
            DailyValueDto dto3 = buildDto(RANGE_END);

            when(assetSourceRepository.findAll()).thenReturn(List.of(hyperliquidSource));
            when(assetRepository.findBySource(hyperliquidSource)).thenReturn(List.of(btcAsset));
            when(hyperliquidProvider.fetchDailyValues(
                eq("BTC"), eq(RANGE_START), eq(RANGE_END), eq("https://api.hyperliquid.xyz/info")
            )).thenReturn(List.of(dto1, dto2, dto3));

            AssetDataSyncService spied = spy(service);
            doNothing().when(spied).upsertDailyValue(any(), any());

            // Act
            spied.syncForDateRange(RANGE_START, RANGE_END);

            // Assert: upsert called once per candle (3 times)
            verify(spied, times(3)).upsertDailyValue(eq(btcAsset), any());
            verify(spied, times(1)).upsertDailyValue(eq(btcAsset), eq(dto1));
            verify(spied, times(1)).upsertDailyValue(eq(btcAsset), eq(dto2));
            verify(spied, times(1)).upsertDailyValue(eq(btcAsset), eq(dto3));
        }

        @Test
        @DisplayName("should continue with remaining assets when one throws an exception")
        void shouldContinueWhenOneAssetThrows() {
            // Arrange
            Asset ethAsset = Asset.builder().id(11L).symbol("ETH").source(hyperliquidSource).build();
            DailyValueDto ethDto = buildDto(RANGE_START);

            when(assetSourceRepository.findAll()).thenReturn(List.of(hyperliquidSource));
            when(assetRepository.findBySource(hyperliquidSource))
                .thenReturn(List.of(btcAsset, ethAsset));

            when(hyperliquidProvider.fetchDailyValues(eq("BTC"), any(), any(), any()))
                .thenThrow(new RuntimeException("network error"));
            when(hyperliquidProvider.fetchDailyValues(eq("ETH"), any(), any(), any()))
                .thenReturn(List.of(ethDto));

            AssetDataSyncService spied = spy(service);
            doNothing().when(spied).upsertDailyValue(any(), any());

            // Act — must not throw
            spied.syncForDateRange(RANGE_START, RANGE_END);

            // Assert: ETH was still synced despite BTC failure
            verify(spied, never()).upsertDailyValue(eq(btcAsset), any());
            verify(spied, times(1)).upsertDailyValue(eq(ethAsset), eq(ethDto));
        }
    }

    // =========================================================================
    // upsertDailyValue() — INSERT vs UPDATE
    // =========================================================================

    @Nested
    @DisplayName("upsertDailyValue() — INSERT vs UPDATE")
    class UpsertDailyValueTests {

        @Test
        @DisplayName("should INSERT a new AssetDailyValue when none exists for (asset, date)")
        void shouldInsertWhenNoExistingRecord() {
            // Arrange
            DailyValueDto dto = buildDto(TEST_DATE);

            // No existing record → Optional.empty()
            when(assetDailyValueRepository.findByAssetAndDate(btcAsset, TEST_DATE))
                .thenReturn(Optional.empty());

            // Act
            service.upsertDailyValue(btcAsset, dto);

            // Assert: save() was called with a newly built entity (not an update of an existing one)
            verify(assetDailyValueRepository, times(1)).save(any(AssetDailyValue.class));
        }

        @Test
        @DisplayName("should UPDATE the existing AssetDailyValue when a record already exists")
        void shouldUpdateWhenExistingRecord() {
            // Arrange
            DailyValueDto dto = buildDto(TEST_DATE);

            AssetDailyValue existing = AssetDailyValue.builder()
                .id(100L)
                .asset(btcAsset)
                .date(TEST_DATE)
                .open(new BigDecimal("90000"))
                .high(new BigDecimal("91000"))
                .low(new BigDecimal("89000"))
                .close(new BigDecimal("90500"))
                .volume(new BigDecimal("500"))
                .build();

            when(assetDailyValueRepository.findByAssetAndDate(btcAsset, TEST_DATE))
                .thenReturn(Optional.of(existing));

            // Act
            service.upsertDailyValue(btcAsset, dto);

            // Assert: the existing entity was mutated and saved
            verify(assetDailyValueRepository, times(1)).save(existing);

            // Verify that the updated values match the DTO
            assertThat(existing.getOpen()).isEqualByComparingTo(dto.getOpen());
            assertThat(existing.getHigh()).isEqualByComparingTo(dto.getHigh());
            assertThat(existing.getLow()).isEqualByComparingTo(dto.getLow());
            assertThat(existing.getClose()).isEqualByComparingTo(dto.getClose());
            assertThat(existing.getVolume()).isEqualByComparingTo(dto.getVolume());
        }
    }

    // =========================================================================
    // syncDailyPrices() — scheduler entry point
    // =========================================================================

    @Nested
    @DisplayName("syncDailyPrices() — scheduler entry point")
    class SyncDailyPricesTests {

        @Test
        @DisplayName("should call syncForDate() exactly once with yesterday's date (J-1)")
        void shouldCallSyncForDateExactlyOnceWithYesterday() {
            // Arrange
            AssetDataSyncService spied = spy(service);
            doNothing().when(spied).syncForDate(any(LocalDate.class));

            // Capture J-1 before invoking the method to avoid any clock-tick between
            // the production call and the assertion.
            LocalDate expectedDate = LocalDate.now().minusDays(1);

            // Act
            spied.syncDailyPrices();

            // Assert: syncForDate() was called exactly once with yesterday's date.
            // times(1) ensures the scheduler wrapper never double-fires.
            ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
            verify(spied, times(1)).syncForDate(captor.capture());
            assertThat(captor.getValue()).isEqualTo(expectedDate);
        }
    }

    // =========================================================================
    // Alert evaluation integration — syncForDate() calls evaluateAlerts()
    // =========================================================================

    @Nested
    @DisplayName("syncForDate() — alert evaluation integration")
    class AlertEvaluationIntegrationTests {

        @Test
        @DisplayName("should call alertService.evaluateAlerts() after syncing data")
        void shouldCallEvaluateAlertsAfterSync() {
            // Arrange: normal sync scenario with one asset
            DailyValueDto dto = buildDto(TEST_DATE);

            when(assetSourceRepository.findAll()).thenReturn(List.of(hyperliquidSource));
            when(assetRepository.findBySource(hyperliquidSource)).thenReturn(List.of(btcAsset));
            when(hyperliquidProvider.fetchDailyValues(
                eq("BTC"), eq(TEST_DATE), eq(TEST_DATE), eq("https://api.hyperliquid.xyz/info")
            )).thenReturn(List.of(dto));

            AssetDataSyncService spied = spy(service);
            doNothing().when(spied).upsertDailyValue(any(), any());

            // Act
            spied.syncForDate(TEST_DATE);

            // Assert: evaluateAlerts() called exactly once with the sync date
            verify(alertService, times(1)).evaluateAlerts(TEST_DATE);
        }

        @Test
        @DisplayName("should call alertService.evaluateAlerts() even when no sources exist")
        void shouldCallEvaluateAlertsEvenWhenNoSources() {
            // Arrange: no sources → sync does nothing, but evaluateAlerts should still run
            when(assetSourceRepository.findAll()).thenReturn(Collections.emptyList());

            // Act
            service.syncForDate(TEST_DATE);

            // Assert: evaluateAlerts() is still called (alerts may exist even without new data)
            verify(alertService, times(1)).evaluateAlerts(TEST_DATE);
        }

        @Test
        @DisplayName("should NOT call alertService.evaluateAlerts() in syncForDateRange()")
        void shouldNotCallEvaluateAlertsInSyncForDateRange() {
            // Arrange: bulk sync scenario
            LocalDate start = LocalDate.of(2024, 1, 15);
            LocalDate end = LocalDate.of(2025, 1, 14);

            when(assetSourceRepository.findAll()).thenReturn(List.of(hyperliquidSource));
            when(assetRepository.findBySource(hyperliquidSource)).thenReturn(List.of(btcAsset));
            when(hyperliquidProvider.fetchDailyValues(
                eq("BTC"), eq(start), eq(end), eq("https://api.hyperliquid.xyz/info")
            )).thenReturn(List.of(buildDto(start)));

            AssetDataSyncService spied = spy(service);
            doNothing().when(spied).upsertDailyValue(any(), any());

            // Act
            spied.syncForDateRange(start, end);

            // Assert: evaluateAlerts() must NOT be called during bulk historical sync
            verify(alertService, never()).evaluateAlerts(any());
        }
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private DailyValueDto buildDto(LocalDate date) {
        return DailyValueDto.builder()
            .date(date)
            .open(new BigDecimal("95000.0"))
            .high(new BigDecimal("96500.0"))
            .low(new BigDecimal("94200.0"))
            .close(new BigDecimal("96000.0"))
            .volume(new BigDecimal("28345.12"))
            .build();
    }
}
