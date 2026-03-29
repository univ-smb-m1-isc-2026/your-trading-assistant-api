package fr.info803.trading_assistant.config;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetSource;
import fr.info803.trading_assistant.repository.AssetDailyValueRepository;
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
 * initialized. It ensures the infrastructure (sources, assets) is set up and
 * synchronizes missing market data.
 *
 * Strategy:
 * 1. Ensure "hyperliquid" and "yahoo" sources exist.
 * 2. Ensure all crypto and stock symbols are registered as Assets.
 * 3. Check the database for the latest available date for BTC.
 * 4. If no data exists, sync the last 365 days (initial seed).
 * 5. If data exists but is older than yesterday, sync the missing range (catch-up).
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
    private final AssetDailyValueRepository assetDailyValueRepository;
    private final AssetDataSyncService assetDataSyncService;
    private final ChartPatternService chartPatternService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[ProdDataInitializer] Checking production data state...");

        // Step 1 — Idempotent infrastructure setup (Sources)
        AssetSource hlSource = assetSourceRepository.findByName(SOURCE_NAME)
                .orElseGet(() -> assetSourceRepository.save(
                        AssetSource.builder().name(SOURCE_NAME).url(SOURCE_URL).build()
                ));

        AssetSource yahooSource = assetSourceRepository.findByName(YAHOO_SOURCE_NAME)
                .orElseGet(() -> assetSourceRepository.save(
                        AssetSource.builder().name(YAHOO_SOURCE_NAME).url(YAHOO_SOURCE_URL).build()
                ));

        // Step 2 — Idempotent asset seeding
        seedAssetsIfMissing(hlSource, CRYPTO_SYMBOLS);
        seedAssetsIfMissing(yahooSource, STOCK_SYMBOLS);

        // Step 3 — Determine synchronization range
        // We use BTC as the reference to know if the app needs to catch up
        LocalDate latestBtcDate = assetDailyValueRepository.findMaxDateBySymbol("BTC").orElse(null);
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate;

        if (latestBtcDate == null) {
            // Case: Fresh database -> Sync 1 year of history
            startDate = LocalDate.now().minusDays(365);
            log.info("[ProdDataInitializer] No data found. Performing initial 1-year sync [{} → {}].", startDate, endDate);
        } else if (latestBtcDate.isBefore(endDate)) {
            // Case: Application was down -> Sync missing days
            startDate = latestBtcDate.plusDays(1);
            log.info("[ProdDataInitializer] Catching up: last BTC date was {}. Syncing [{} → {}].", latestBtcDate, startDate, endDate);
        } else {
            // Case: Up to date
            log.info("[ProdDataInitializer] Data is up to date (last sync: {}). skipping sync.", latestBtcDate);
            return;
        }

        // Step 4 — Synchronize data for the calculated range
        assetDataSyncService.syncForDateRange(startDate, endDate);

        // Step 5 — Evaluate chart patterns for the newly synced data
        log.info("[ProdDataInitializer] Evaluating chart patterns for the synced range [{} → {}]...", startDate, endDate);
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            chartPatternService.evaluatePatterns(currentDate);
            currentDate = currentDate.plusDays(1);
        }

        log.info("[ProdDataInitializer] Production-grade initialization complete.");
    }

    private void seedAssetsIfMissing(AssetSource source, List<String> symbols) {
        for (String symbol : symbols) {
            if (assetRepository.findBySymbol(symbol).isEmpty()) {
                assetRepository.save(Asset.builder()
                        .symbol(symbol)
                        .source(source)
                        .build());
                log.debug("[ProdDataInitializer] Seeded missing asset: {}", symbol);
            }
        }
    }
}
