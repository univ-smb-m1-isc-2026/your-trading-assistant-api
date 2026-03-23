package fr.info803.trading_assistant.config;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetSource;
import fr.info803.trading_assistant.repository.AssetRepository;
import fr.info803.trading_assistant.repository.AssetSourceRepository;
import fr.info803.trading_assistant.service.AssetDataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Data initializer for the production profile.
 *
 * This component runs once at startup after the Spring context is fully
 * initialized. It checks if the database is empty by looking for the
 * "hyperliquid" asset source. If not present, it seeds the database with the
 * core asset source, base assets (BTC, ETH, etc.), and triggers a bulk
 * synchronization of one year of historical market data.
 *
 * Unlike DevDataInitializer, this class does NOT seed demo users or triggered
 * alerts.
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProdDataInitializer implements ApplicationRunner {

    private static final String SOURCE_NAME = "hyperliquid";
    private static final String SOURCE_URL = "https://api.hyperliquid.xyz/info";

    private static final List<String> SYMBOLS = List.of(
            "BTC", "ETH", "ATOM", "DYDX", "SOL", "AVAX", "BNB", "APE", "OP", "LTC",
            "ARB", "DOGE", "INJ", "SUI", "kPEPE", "CRV", "LDO", "LINK", "STX", "CFX",
            "GMX", "SNX", "XRP", "BCH", "APT", "AAVE", "COMP", "WLD", "YGG", "TRX",
            "kSHIB", "UNI", "SEI", "RUNE", "ZRO", "DOT", "BANANA", "TRB", "FTT", "ARK",
            "BIGTIME", "KAS", "BLUR", "TIA", "BSV", "ADA", "TON", "MINA", "POLYX", "GAS"
    );
    private final AssetSourceRepository assetSourceRepository;
    private final AssetRepository assetRepository;
    private final AssetDataSyncService assetDataSyncService;

    @Override
    public void run(ApplicationArguments args) {
        // Idempotency check: if the source already exists, we assume initialization was already done.
        if (assetSourceRepository.findByName(SOURCE_NAME).isPresent()) {
            log.info("[ProdDataInitializer] Data already present, skipping initialization.");
            return;
        }

        log.info("[ProdDataInitializer] Initializing production-grade data...");

        // Step 1 — Create the Hyperliquid AssetSource
        AssetSource source = assetSourceRepository.save(
                AssetSource.builder()
                        .name(SOURCE_NAME)
                        .url(SOURCE_URL)
                        .build()
        );
        log.info("[ProdDataInitializer] Created AssetSource: id={} name={}", source.getId(), source.getName());

        // Step 2 — Create the base assets
        List<Asset> assets = SYMBOLS.stream()
                .map(symbol -> Asset.builder()
                .symbol(symbol)
                .source(source)
                .build())
                .toList();

        assetRepository.saveAll(assets);
        log.info("[ProdDataInitializer] Created {} base assets: {}", assets.size(), SYMBOLS);

        // Step 3 — Synchronize 1 year of historical OHLCV data
        // We sync up to yesterday to ensure we only get completed daily candles.
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = LocalDate.now().minusDays(365);

        log.info("[ProdDataInitializer] Triggering bulk sync for 1 year of history [{} → {}]...",
                startDate, endDate);

        assetDataSyncService.syncForDateRange(startDate, endDate);

        log.info("[ProdDataInitializer] Production-grade initialization complete.");
    }
}
