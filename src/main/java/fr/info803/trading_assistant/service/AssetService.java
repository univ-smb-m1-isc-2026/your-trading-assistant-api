package fr.info803.trading_assistant.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import fr.info803.trading_assistant.dto.AssetSummaryResponse;
import fr.info803.trading_assistant.dto.CandleResponse;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetDailyValue;
import fr.info803.trading_assistant.exception.AssetNotFoundException;
import fr.info803.trading_assistant.repository.AssetDailyValueRepository;
import fr.info803.trading_assistant.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
    Service métier pour les endpoints asset.

    Contrats :

    1. getAssetSummaries()
       - Retourne tous les assets avec leur dernier prix connu.
       - Stratégie : 2 queries SQL (scalable pour des centaines d'assets) :
         Query 1 : SELECT * FROM asset (tous les assets)
         Query 2 : SELECT * FROM asset_daily_value adv
                   WHERE adv.date = (SELECT MAX(...) ...) (dernière bougie par asset)
       - Les assets sans prix reçoivent lastPrice=null, lastDate=null.
       - Fusion en mémoire via une Map<assetId, latestCandle>.
       - Tri : par symbol ASC pour une cohérence client.

    2. getCandles(String symbol)
       - Retourne toutes les bougies d'un asset sur les 12 derniers mois.
       - Lève AssetNotFoundException si le symbol est inconnu.
       - Filtre : date >= LocalDate.now().minusYears(1)
       - Tri : par date ASC (plus ancienne d'abord).
       - Utilité : analyse historique, graphiques OHLC.

    Pourquoi pas de @Transactional ?
       - getAssetSummaries() : requête read-only simple, transaction implicite est OK.
       - getCandles() : idem, lire une liste sans modification.
       - Les lazy-loading (Asset.source via assetRepository) ne risquent pas de
         LazyInitializationException car les DTOs sont construits dans le contexte
         de la transaction implicite (avant le retour).
*/
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetDailyValueRepository assetDailyValueRepository;

    /*
        Récupère tous les assets avec leur dernier prix enregistré en base.

        Flux :
          1. Charge toutes les dernières bougies (une par asset unique).
          2. Charge tous les assets.
          3. Construit une Map id → lastCandle pour fusion rapide (O(1)).
          4. Pour chaque asset : cherche sa bougie latest dans la map.
               Si trouvée : lastPrice et lastDate du close/date.
               Si absente : lastPrice=null, lastDate=null.
          5. Trie par symbol ASC (cohérence client).

        Complexité : O(n log n) pour le tri (n = nb assets).
        Queries SQL : 2 (fixed cost, peu importe le volume).
        Mémoire : O(n).
    */
    public List<AssetSummaryResponse> getAssetSummaries() {
        log.info("Fetching all asset summaries");

        // Query 1 : dernière bougie pour chaque asset (sous-requête corrélée en JPQL)
        List<AssetDailyValue> latestCandles = assetDailyValueRepository.findLatestForAllAssets();

        // Lookup O(1) : Map asset.id → dernière bougie
        Map<Long, AssetDailyValue> latestByAssetId = latestCandles.stream()
            .collect(Collectors.toMap(
                adv -> adv.getAsset().getId(),
                adv -> adv
            ));

        // Query 2 : tous les assets
        List<Asset> allAssets = assetRepository.findAll();

        // Fusion en mémoire : pour chaque asset, cherche sa dernière bougie
        List<AssetSummaryResponse> summaries = allAssets.stream()
            .map(asset -> {
                AssetDailyValue latest = latestByAssetId.get(asset.getId());
                return AssetSummaryResponse.builder()
                    .symbol(asset.getSymbol())
                    .lastPrice(latest != null ? latest.getClose() : null)
                    .lastDate(latest != null ? latest.getDate() : null)
                    .build();
            })
            .sorted((a, b) -> a.getSymbol().compareTo(b.getSymbol()))
            .toList();

        log.info("Fetched {} asset summaries", summaries.size());
        return summaries;
    }

    /*
        Récupère toutes les bougies d'un asset sur les 12 derniers mois.

        Lève AssetNotFoundException si le symbol est inconnu.

        Flux :
          1. Résout le symbol en Asset (ou 404).
          2. Calcule fromDate = J-1 an.
          3. Requête : SELECT * FROM asset_daily_value
                       WHERE asset_id = ? AND date >= fromDate
                       ORDER BY date ASC
          4. Mappe en CandleResponse.
          5. Retourne.

        Complexité : O(n log n) pour le tri (n = nb bougies).
        Queries SQL : 1 (lookup + fetch candles).
        Cas extrême : 365+ bougies / asset (acceptable).
    */
    public List<CandleResponse> getCandles(String symbol) {
        log.info("Fetching candles for symbol={}", symbol);

        // Résout le symbol en asset
        Asset asset = assetRepository.findBySymbol(symbol)
            .orElseThrow(() -> new AssetNotFoundException(symbol));

        // Filtre date >= J-1 an
        LocalDate fromDate = LocalDate.now().minusYears(1);
        List<AssetDailyValue> candles = assetDailyValueRepository
            .findByAssetAndDateGreaterThanEqualOrderByDateAsc(asset, fromDate);

        // Mappe en DTO
        List<CandleResponse> responses = candles.stream()
            .map(adv -> CandleResponse.builder()
                .date(adv.getDate())
                .open(adv.getOpen())
                .high(adv.getHigh())
                .low(adv.getLow())
                .close(adv.getClose())
                .volume(adv.getVolume())
                .build())
            .toList();

        log.info("Fetched {} candles for symbol={}", responses.size(), symbol);
        return responses;
    }
}
