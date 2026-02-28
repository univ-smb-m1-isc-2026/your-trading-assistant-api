package fr.info803.trading_assistant.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Asset and AssetDailyValue entities.
 *
 * Tests Lombok-generated constructors, builders, and getters/setters
 * in isolation (no Spring context, no database).
 *
 * Why test entities?
 *   - Verifies that Lombok annotations are processed correctly by the compiler.
 *   - Documents the expected field structure for future contributors.
 *   - Catches regressions if fields are renamed or removed.
 */
@DisplayName("Asset Entity Unit Tests")
class AssetTest {

    // =========================================================================
    // AssetSource builder and accessors
    // =========================================================================

    @Nested
    @DisplayName("AssetSource")
    class AssetSourceTests {

        @Test
        @DisplayName("should create AssetSource with builder")
        void shouldCreateWithBuilder() {
            AssetSource source = AssetSource.builder()
                .id(1L)
                .name("hyperliquid")
                .url("https://api.hyperliquid.xyz/info")
                .build();

            assertThat(source.getId()).isEqualTo(1L);
            assertThat(source.getName()).isEqualTo("hyperliquid");
            assertThat(source.getUrl()).isEqualTo("https://api.hyperliquid.xyz/info");
        }

        @Test
        @DisplayName("should create AssetSource with no-args constructor and setters")
        void shouldCreateWithNoArgsAndSetters() {
            AssetSource source = new AssetSource();
            source.setId(2L);
            source.setName("alphavantage");
            source.setUrl("https://www.alphavantage.co/query");

            assertThat(source.getId()).isEqualTo(2L);
            assertThat(source.getName()).isEqualTo("alphavantage");
            assertThat(source.getUrl()).isEqualTo("https://www.alphavantage.co/query");
        }

        @Test
        @DisplayName("should create AssetSource with all-args constructor")
        void shouldCreateWithAllArgsConstructor() {
            AssetSource source = new AssetSource(3L, "testSource", "https://example.com");

            assertThat(source.getId()).isEqualTo(3L);
            assertThat(source.getName()).isEqualTo("testSource");
            assertThat(source.getUrl()).isEqualTo("https://example.com");
        }

        @Test
        @DisplayName("should allow null fields via builder")
        void shouldAllowNullFields() {
            AssetSource source = AssetSource.builder().build();

            assertThat(source.getId()).isNull();
            assertThat(source.getName()).isNull();
            assertThat(source.getUrl()).isNull();
        }
    }

    // =========================================================================
    // Asset builder and accessors
    // =========================================================================

    @Nested
    @DisplayName("Asset")
    class AssetEntityTests {

        private AssetSource buildSource() {
            return AssetSource.builder()
                .id(1L)
                .name("hyperliquid")
                .url("https://api.hyperliquid.xyz/info")
                .build();
        }

        @Test
        @DisplayName("should create Asset with builder including source")
        void shouldCreateWithBuilder() {
            AssetSource source = buildSource();
            Asset asset = Asset.builder()
                .id(10L)
                .symbol("BTC")
                .source(source)
                .build();

            assertThat(asset.getId()).isEqualTo(10L);
            assertThat(asset.getSymbol()).isEqualTo("BTC");
            assertThat(asset.getSource()).isSameAs(source);
        }

        @Test
        @DisplayName("should create Asset with no-args constructor and setters")
        void shouldCreateWithNoArgsAndSetters() {
            AssetSource source = buildSource();
            Asset asset = new Asset();
            asset.setId(11L);
            asset.setSymbol("ETH");
            asset.setSource(source);

            assertThat(asset.getId()).isEqualTo(11L);
            assertThat(asset.getSymbol()).isEqualTo("ETH");
            assertThat(asset.getSource()).isSameAs(source);
        }

        @Test
        @DisplayName("should create Asset with all-args constructor")
        void shouldCreateWithAllArgsConstructor() {
            AssetSource source = buildSource();
            Asset asset = new Asset(12L, "AERO", source);

            assertThat(asset.getId()).isEqualTo(12L);
            assertThat(asset.getSymbol()).isEqualTo("AERO");
            assertThat(asset.getSource()).isSameAs(source);
        }

        @Test
        @DisplayName("should allow null source via builder")
        void shouldAllowNullSource() {
            Asset asset = Asset.builder().id(13L).symbol("SAGA").source(null).build();

            assertThat(asset.getSource()).isNull();
        }

        @Test
        @DisplayName("two Asset instances with same data should not be the same reference")
        void shouldBeDistinctInstances() {
            AssetSource source = buildSource();
            Asset a1 = Asset.builder().id(1L).symbol("BTC").source(source).build();
            Asset a2 = Asset.builder().id(1L).symbol("BTC").source(source).build();

            assertThat(a1).isNotSameAs(a2);
            assertThat(a1.getSymbol()).isEqualTo(a2.getSymbol());
        }
    }

    // =========================================================================
    // AssetDailyValue builder and accessors
    // =========================================================================

    @Nested
    @DisplayName("AssetDailyValue")
    class AssetDailyValueTests {

        private Asset buildAsset() {
            AssetSource source = AssetSource.builder()
                .id(1L).name("hyperliquid").url("https://api.hyperliquid.xyz/info").build();
            return Asset.builder().id(10L).symbol("BTC").source(source).build();
        }

        @Test
        @DisplayName("should create AssetDailyValue with builder")
        void shouldCreateWithBuilder() {
            Asset asset = buildAsset();
            LocalDate date = LocalDate.of(2025, 1, 15);

            AssetDailyValue adv = AssetDailyValue.builder()
                .id(100L)
                .asset(asset)
                .date(date)
                .open(new BigDecimal("95000.0"))
                .high(new BigDecimal("96500.0"))
                .low(new BigDecimal("94200.0"))
                .close(new BigDecimal("96000.0"))
                .volume(new BigDecimal("28345.12"))
                .build();

            assertThat(adv.getId()).isEqualTo(100L);
            assertThat(adv.getAsset()).isSameAs(asset);
            assertThat(adv.getDate()).isEqualTo(date);
            assertThat(adv.getOpen()).isEqualByComparingTo("95000.0");
            assertThat(adv.getHigh()).isEqualByComparingTo("96500.0");
            assertThat(adv.getLow()).isEqualByComparingTo("94200.0");
            assertThat(adv.getClose()).isEqualByComparingTo("96000.0");
            assertThat(adv.getVolume()).isEqualByComparingTo("28345.12");
        }

        @Test
        @DisplayName("should support setters for all OHLCV fields")
        void shouldSupportSetters() {
            AssetDailyValue adv = new AssetDailyValue();
            Asset asset = buildAsset();
            LocalDate date = LocalDate.of(2025, 6, 1);

            adv.setId(200L);
            adv.setAsset(asset);
            adv.setDate(date);
            adv.setOpen(new BigDecimal("10000"));
            adv.setHigh(new BigDecimal("11000"));
            adv.setLow(new BigDecimal("9500"));
            adv.setClose(new BigDecimal("10500"));
            adv.setVolume(new BigDecimal("1000"));

            assertThat(adv.getId()).isEqualTo(200L);
            assertThat(adv.getAsset()).isSameAs(asset);
            assertThat(adv.getDate()).isEqualTo(date);
            assertThat(adv.getOpen()).isEqualByComparingTo("10000");
            assertThat(adv.getHigh()).isEqualByComparingTo("11000");
            assertThat(adv.getLow()).isEqualByComparingTo("9500");
            assertThat(adv.getClose()).isEqualByComparingTo("10500");
            assertThat(adv.getVolume()).isEqualByComparingTo("1000");
        }

        @Test
        @DisplayName("should create AssetDailyValue with all-args constructor")
        void shouldCreateWithAllArgsConstructor() {
            Asset asset = buildAsset();
            LocalDate date = LocalDate.of(2025, 3, 10);

            AssetDailyValue adv = new AssetDailyValue(
                300L,
                asset,
                date,
                new BigDecimal("50000"),
                new BigDecimal("51000"),
                new BigDecimal("49000"),
                new BigDecimal("50500"),
                new BigDecimal("500")
            );

            assertThat(adv.getId()).isEqualTo(300L);
            assertThat(adv.getDate()).isEqualTo(date);
            assertThat(adv.getOpen()).isEqualByComparingTo("50000");
            assertThat(adv.getClose()).isEqualByComparingTo("50500");
        }

        @Test
        @DisplayName("high should be greater than or equal to low in a valid candle")
        void highShouldBeGreaterThanOrEqualToLow() {
            // This is a domain invariant — not enforced by the entity itself (no @AssertTrue)
            // but we document it here so future validators know the contract.
            AssetDailyValue adv = AssetDailyValue.builder()
                .high(new BigDecimal("96500"))
                .low(new BigDecimal("94200"))
                .build();

            assertThat(adv.getHigh()).isGreaterThanOrEqualTo(adv.getLow());
        }

        @Test
        @DisplayName("should allow null id before persistence (auto-generated)")
        void shouldAllowNullId() {
            AssetDailyValue adv = AssetDailyValue.builder().build();
            assertThat(adv.getId()).isNull();
        }
    }
}
