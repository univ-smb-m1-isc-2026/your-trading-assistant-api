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
import fr.info803.trading_assistant.service.ChartPatternService;
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

    private static final String YAHOO_SOURCE_NAME = "yahoo";
    private static final String YAHOO_SOURCE_URL  = "https://query1.finance.yahoo.com/v7/finance/quote";

    private static final List<String> CRYPTO_SYMBOLS = List.of(
            "BTC", "ETH", "ATOM", "DYDX", "SOL", "AVAX", "BNB", "APE", "OP", "LTC",
            "ARB", "DOGE", "INJ", "SUI", "kPEPE", "CRV", "LDO", "LINK", "STX", "CFX",
            "GMX", "SNX", "XRP", "BCH", "APT", "AAVE", "COMP", "WLD", "YGG", "TRX",
            "kSHIB", "UNI", "SEI", "RUNE", "ZRO", "DOT", "BANANA", "TRB", "FTT", "ARK",
            "BIGTIME", "KAS", "BLUR", "TIA", "BSV", "ADA", "TON", "MINA", "POLYX", "GAS"
    );

    private static final List<String> STOCK_SYMBOLS = List.of(
            "AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "TSLA", "META", "BRK-B", "UNH", "JNJ",
            "JPM", "V", "PG", "XOM", "MA", "HD", "CVX", "ABBV", "LLY", "PFE",
            "MRK", "COST", "PEP", "KO", "TMO", "AVGO", "ORCL", "AZN", "CSCO", "ACN",
            "NKE", "DHR", "MCD", "LIN", "ABT", "DIS", "ADBE", "PM", "WMT", "CRM",
            "TXN", "UPS", "NEE", "MS", "VZ", "RTX", "HON", "AMGN", "COP", "CAT"
    );

    private final AssetSourceRepository assetSourceRepository;
    private final AssetRepository assetRepository;
    private final AssetDataSyncService assetDataSyncService;
    private final ChartPatternService chartPatternService;

    @Override
    public void run(ApplicationArguments args) {
        // Idempotency check: if the source already exists, we assume initialization was already done.
        if (assetSourceRepository.findByName(SOURCE_NAME).isPresent()) {
            log.info("[ProdDataInitializer] Data already present, skipping initialization.");
            return;
        }

        log.info("[ProdDataInitializer] Initializing production-grade data...");

        // Step 1 — Create AssetSources
        AssetSource hlSource = assetSourceRepository.save(
                AssetSource.builder()
                        .name(SOURCE_NAME)
                        .url(SOURCE_URL)
                        .build()
        );
        log.info("[ProdDataInitializer] Created AssetSource: {}", hlSource.getName());

        AssetSource yahooSource = assetSourceRepository.save(
                AssetSource.builder()
                        .name(YAHOO_SOURCE_NAME)
                        .url(YAHOO_SOURCE_URL)
                        .build()
        );
        log.info("[ProdDataInitializer] Created AssetSource: {}", yahooSource.getName());

        // Step 2 — Create assets
        seedAssets(hlSource, CRYPTO_SYMBOLS);
        seedAssets(yahooSource, STOCK_SYMBOLS);

        // Step 3 — Synchronize 1 year of historical OHLCV data
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = LocalDate.now().minusDays(365);

        log.info("[ProdDataInitializer] Triggering bulk sync for 1 year of history [{} → {}]...",
                startDate, endDate);

        assetDataSyncService.syncForDateRange(startDate, endDate);

        // Step 4 — Evaluate chart patterns for the synced historical data
        log.info("[ProdDataInitializer] Evaluating chart patterns for the past year [{} → {}]...",
                startDate, endDate);
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            chartPatternService.evaluatePatterns(currentDate);
            currentDate = currentDate.plusDays(1);
        }

        log.info("[ProdDataInitializer] Production-grade initialization complete.");
    }

    private void seedAssets(AssetSource source, List<String> symbols) {
        List<Asset> assets = symbols.stream()
                .map(symbol -> Asset.builder()
                        .symbol(symbol)
                        .source(source)
                        .build())
                .toList();
        assetRepository.saveAll(assets);
        log.info("[ProdDataInitializer] Created {} assets for source {}", assets.size(), source.getName());
    }
}
