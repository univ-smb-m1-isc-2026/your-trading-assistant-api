package fr.info803.trading_assistant.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import fr.info803.trading_assistant.dto.DailyValueDto;
import lombok.extern.slf4j.Slf4j;

/*
    Implémentation du Strategy Pattern pour Yahoo Finance via WebClient direct.

    Yahoo Finance est un fournisseur classique de données boursières.
    Plutôt que d'utiliser une bibliothèque tierce, nous utilisons ici WebClient pour
    appeler directement l'API JSON v8/finance/chart.

    Pourquoi cette approche ?
      - Plus stable : les bibliothèques tierces cassent souvent lors des changements de Yahoo.
      - Contrôle total : permet d'injecter un User-Agent de navigateur pour éviter les erreurs 429.
      - Performance : un seul appel récupère l'intégralité de la période (1 an).
      - Cohérence : utilise le même moteur (WebClient) que HyperliquidAssetDataProvider.

    Gestion des erreurs :
      - En cas d'erreur 429 ou de réseau, retourne une liste vide pour ne pas bloquer
        le reste de la synchronisation nocturne.
*/
@Slf4j
@Component
public class YahooAssetDataProvider implements AssetDataProvider {

    private static final String SOURCE_NAME = "yahoo";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final WebClient webClient;

    public YahooAssetDataProvider(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public List<DailyValueDto> fetchDailyValues(String symbol, LocalDate startDate, LocalDate endDate, String apiUrl) {
        // Conversion LocalDate -> Epoch Seconds (requis par Yahoo v8)
        long period1 = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().getEpochSecond();
        long period2 = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().getEpochSecond();

        // L'URL attendue est de la forme : https://query1.finance.yahoo.com/v8/finance/chart/AAPL?period1=...&period2=...&interval=1d
        String url = String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s?period1=%d&period2=%d&interval=1d",
            symbol, period1, period2);

        try {
            log.info("Yahoo Finance: fetching {} for range [{} → {}]", symbol, startDate, endDate);

            YahooChartResponse response = webClient.get()
                .uri(url)
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(YahooChartResponse.class)
                .block();

            if (response == null || response.chart() == null || response.chart().result() == null || response.chart().result().isEmpty()) {
                log.warn("Yahoo Finance: no data found for symbol={} at {}", symbol, url);
                return Collections.emptyList();
            }

            YahooChartResponse.Result result = response.chart().result().get(0);
            List<Long> timestamps = result.timestamp();
            YahooChartResponse.Quote quote = result.indicators().quote().get(0);

            if (timestamps == null || quote == null) {
                return Collections.emptyList();
            }

            List<DailyValueDto> dailyValues = new ArrayList<>();
            for (int i = 0; i < timestamps.size(); i++) {
                // Yahoo peut renvoyer des valeurs nulles pour certains jours (trading suspendu)
                if (quote.close().get(i) == null) continue;

                dailyValues.add(DailyValueDto.builder()
                    .date(LocalDate.ofInstant(Instant.ofEpochSecond(timestamps.get(i)), ZoneOffset.UTC))
                    .open(quote.open().get(i))
                    .high(quote.high().get(i))
                    .low(quote.low().get(i))
                    .close(quote.close().get(i))
                    .volume(quote.volume().get(i) != null ? BigDecimal.valueOf(quote.volume().get(i)) : BigDecimal.ZERO)
                    .build());
            }

            return dailyValues;

        } catch (Exception e) {
            log.error("Yahoo Finance: failed to fetch data for symbol={} — {}: {}",
                symbol, e.getClass().getSimpleName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // Records pour le mapping JSON de Yahoo Finance v8
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record YahooChartResponse(Chart chart) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Chart(List<Result> result) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Result(List<Long> timestamp, Indicators indicators) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Indicators(List<Quote> quote) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Quote(
            List<BigDecimal> open,
            List<BigDecimal> high,
            List<BigDecimal> low,
            List<BigDecimal> close,
            List<Long> volume
        ) {}
    }
}
