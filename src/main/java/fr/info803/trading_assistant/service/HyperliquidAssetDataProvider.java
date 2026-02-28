package fr.info803.trading_assistant.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import fr.info803.trading_assistant.dto.DailyValueDto;
import lombok.extern.slf4j.Slf4j;

/*
    Implémentation du Strategy Pattern pour l'API Hyperliquid.

    Hyperliquid est un exchange décentralisé de perpetual futures.
    Documentation API : https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api

    Endpoint utilisé : POST {apiUrl}
    Type de requête  : candleSnapshot (chandeliers OHLCV)
    Intervalle       : "1d" (journalier)

    Format de la requête envoyée à Hyperliquid :
    {
      "type": "candleSnapshot",
      "req": {
        "coin": "BTC",
        "interval": "1d",
        "startTime": 1700000000000,   ← timestamp UTC millis du début du jour cible
        "endTime":   1700086399999    ← timestamp UTC millis de la fin du jour cible
      }
    }

    Format de la réponse (liste de bougies) :
    [
      {
        "t": 1700000000000,  ← openTime
        "T": 1700086399999,  ← closeTime
        "o": "36500.0",      ← open
        "h": "37500.0",      ← high
        "l": "36000.0",      ← low
        "c": "37000.5",      ← close
        "v": "1234.56",      ← volume
        "s": "BTC"           ← symbol
      }
    ]

    Pourquoi WebClient et non RestTemplate ?
      - WebClient est le client HTTP moderne de Spring (Spring 5+).
      - RestTemplate est déprécié depuis Spring 5.
      - On utilise .block() pour rester en mode synchrone (acceptable dans un scheduler).
      - Le projet a déjà spring-boot-starter-webflux dans le pom.xml → WebClient disponible.

    Gestion des erreurs :
      - Toute exception (réseau, parsing, timeout) est catchée et loggée.
      - On retourne Collections.emptyList() pour que le SyncService puisse continuer
        avec les autres assets sans interruption.
*/
@Slf4j
@Component
public class HyperliquidAssetDataProvider implements AssetDataProvider {

    private static final String SOURCE_NAME = "hyperliquid";

    private final WebClient webClient;

    // WebClient.Builder est auto-configuré par Spring Boot quand webflux est dans le classpath.
    // On l'injecte ici pour bénéficier des auto-configurations (codecs, timeouts, etc.).
    public HyperliquidAssetDataProvider(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /*
        Retourne "hyperliquid" — doit correspondre EXACTEMENT à AssetSource.name en DB.
        Le SyncService s'en sert pour router chaque source vers le bon provider.
    */
    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    /*
        Récupère les données OHLCV journalières pour un symbole donné sur un intervalle de dates.

        Paramètres :
          - symbol    : ticker de l'asset (ex: "BTC", "ETH")
          - startDate : début de l'intervalle, inclus (ex: LocalDate.now().minusDays(365))
          - endDate   : fin de l'intervalle, inclus (ex: LocalDate.now().minusDays(1))
          - apiUrl    : URL lue depuis AssetSource.url en DB
                        (ex: "https://api.hyperliquid.xyz/info")

        L'API retourne toutes les bougies journalières dans l'intervalle en un seul appel.
        Pour un seul jour (scheduler nocturne), passer startDate == endDate.

        Chaque bougie est convertie en DailyValueDto avec la date déduite du timestamp
        d'ouverture (champ "t") retourné par l'API — pas besoin de l'interpoler côté client.
    */
    @Override
    public List<DailyValueDto> fetchDailyValues(String symbol, LocalDate startDate, LocalDate endDate, String apiUrl) {
        // Calcul des timestamps UTC en millisecondes pour l'intervalle cible.
        // startTime = minuit UTC du jour startDate (ex: 2025-01-14T00:00:00Z)
        // endTime   = 1ms avant minuit UTC du lendemain de endDate (ex: 2025-01-15T23:59:59.999Z)
        long startTime = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long endTime = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;

        HyperliquidCandleRequest requestBody = new HyperliquidCandleRequest(
            "candleSnapshot",
            new HyperliquidCandleRequest.Req(symbol, "1d", startTime, endTime)
        );

        try {
            log.info("Hyperliquid: fetching {} for range [{} → {}]", symbol, startDate, endDate);

            List<HyperliquidCandle> candles = webClient.post()
                .uri(apiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                // ParameterizedTypeReference nécessaire pour désérialiser List<T>
                // car l'effacement de type Java (type erasure) empêche List<HyperliquidCandle>.class
                .bodyToMono(new ParameterizedTypeReference<List<HyperliquidCandle>>() {})
                // .block() : convertit le Mono réactif en appel synchrone.
                // Acceptable ici car le scheduler n'est pas dans un pipeline réactif.
                .block();

            if (candles == null || candles.isEmpty()) {
                log.warn("Hyperliquid: no candle data returned for symbol={} range=[{} → {}]",
                    symbol, startDate, endDate);
                return Collections.emptyList();
            }

            // Chaque bougie porte son propre timestamp d'ouverture (champ "t").
            // On en déduit la date UTC du jour correspondant pour le DailyValueDto,
            // au lieu de réutiliser un paramètre "date" unique.
            return candles.stream()
                .map(candle -> DailyValueDto.builder()
                    .date(LocalDate.ofInstant(
                        Instant.ofEpochMilli(candle.openTime()), ZoneOffset.UTC))
                    .open(new BigDecimal(candle.open()))
                    .high(new BigDecimal(candle.high()))
                    .low(new BigDecimal(candle.low()))
                    .close(new BigDecimal(candle.close()))
                    .volume(new BigDecimal(candle.volume()))
                    .build())
                .toList();

        } catch (Exception e) {
            log.error("Hyperliquid: failed to fetch data for symbol={} range=[{} → {}] — {}",
                symbol, startDate, endDate, e.getMessage());
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // Classes internes pour le format JSON de l'API Hyperliquid
    // -------------------------------------------------------------------------

    /*
        Corps de la requête POST vers Hyperliquid.

        Pourquoi des records Java ?
          - Immuables par nature (adapté à un objet de requête ponctuel).
          - Jackson sérialise les records en JSON via leurs accesseurs (type(), req()).
          - Les noms d'accesseurs ("type", "req", "coin"...) correspondent aux clés JSON attendues.
    */
    private record HyperliquidCandleRequest(String type, Req req) {
        private record Req(String coin, String interval, long startTime, long endTime) {}
    }

    /*
        Représentation d'une bougie dans la réponse de Hyperliquid.

        @JsonProperty : Hyperliquid utilise des clés à une seule lettre ("o", "h", "l"...).
        On les mappe vers des noms explicites pour la lisibilité du code interne.
        @JsonIgnoreProperties(ignoreUnknown = true) : ignore les champs "n", "i", etc.
        que l'on n'utilise pas, sans planter la désérialisation.
    */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HyperliquidCandle(
        @JsonProperty("t") long openTime,
        @JsonProperty("T") long closeTime,
        @JsonProperty("o") String open,
        @JsonProperty("h") String high,
        @JsonProperty("l") String low,
        @JsonProperty("c") String close,
        @JsonProperty("v") String volume,
        @JsonProperty("s") String symbol
    ) {}
}
