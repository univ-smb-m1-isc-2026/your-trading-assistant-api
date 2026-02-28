package fr.info803.trading_assistant.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetDailyValue;
import fr.info803.trading_assistant.entity.AssetSource;
import fr.info803.trading_assistant.repository.AssetDailyValueRepository;
import fr.info803.trading_assistant.repository.AssetRepository;
import fr.info803.trading_assistant.repository.AssetSourceRepository;
import lombok.RequiredArgsConstructor;

/*
    Endpoint de diagnostic temporaire — profil "dev" uniquement.

    Expose GET /dev/dump qui retourne un snapshot JSON de toute la base H2 :
      - asset_source
      - asset
      - asset_daily_value

    Pourquoi les repositories sont injectés directement ici (exception à la règle) ?
      Ce controller est un outil de dev pur, sans logique métier. Ajouter une couche
      service serait du bruit sans valeur ajoutée. L'annotation @Profile("dev") garantit
      que ce bean n'existe pas en production.

    Gestion du lazy loading :
      Asset.source  et AssetDailyValue.asset  sont FetchType.LAZY.
      Pour éviter une LazyInitializationException sans @Transactional, on adopte
      une stratégie en deux passes :
        1. Charger toutes les entités parentes dans une Map<id, entité>.
        2. Lors du mapping des enfants, accéder à l'id du proxy lazy
           (Hibernate résout getId() sur un proxy sans déclencher de requête SQL
           car la FK est déjà connue), puis lookup dans la Map.
      → Aucune requête N+1, aucune session transactionnelle requise.
*/
@RestController
@RequestMapping("/dev")
@Profile("dev")
@RequiredArgsConstructor
public class DevDumpController {

    private final AssetSourceRepository assetSourceRepository;
    private final AssetRepository assetRepository;
    private final AssetDailyValueRepository assetDailyValueRepository;

    // -------------------------------------------------------------------------
    // Records de réponse (DTOs internes, auto-sérialisés par Jackson)
    // -------------------------------------------------------------------------

    record SourceRow(Long id, String name, String url) {}

    record AssetRow(Long id, String symbol, Long sourceId, String sourceName) {}

    record DailyValueRow(
        Long id,
        String symbol,
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume
    ) {}

    record DbDump(
        List<SourceRow>      sources,
        List<AssetRow>       assets,
        List<DailyValueRow>  dailyValues
    ) {}

    // -------------------------------------------------------------------------
    // Endpoint principal
    // -------------------------------------------------------------------------

    @GetMapping("/dump")
    public DbDump dump() {
        // --- 1. Sources ---
        List<AssetSource> sources = assetSourceRepository.findAll();
        List<SourceRow> sourceRows = sources.stream()
            .map(s -> new SourceRow(s.getId(), s.getName(), s.getUrl()))
            .toList();

        // Map id → AssetSource pour le lookup lors du mapping des assets
        Map<Long, AssetSource> sourceById = sources.stream()
            .collect(Collectors.toMap(AssetSource::getId, s -> s));

        // --- 2. Assets ---
        List<Asset> assets = assetRepository.findAll();

        // Astuce lazy : asset.getSource() renvoie un proxy Hibernate.
        // getId() sur un proxy est résolu localement (FK déjà en mémoire),
        // sans déclencher de requête SQL vers asset_source.
        // On utilise ensuite la Map pour récupérer le name sans requête supplémentaire.
        List<AssetRow> assetRows = assets.stream()
            .map(a -> {
                Long sourceId = a.getSource().getId();
                String sourceName = sourceById.containsKey(sourceId)
                    ? sourceById.get(sourceId).getName()
                    : "unknown";
                return new AssetRow(a.getId(), a.getSymbol(), sourceId, sourceName);
            })
            .toList();

        // Map id → Asset pour le lookup lors du mapping des daily values
        Map<Long, Asset> assetById = assets.stream()
            .collect(Collectors.toMap(Asset::getId, a -> a));

        // --- 3. Daily values ---
        List<AssetDailyValue> dailyValues = assetDailyValueRepository.findAll();
        List<DailyValueRow> dailyValueRows = dailyValues.stream()
            .map(dv -> {
                Long assetId = dv.getAsset().getId(); // proxy safe : getId() ne charge pas l'asset
                String symbol = assetById.containsKey(assetId)
                    ? assetById.get(assetId).getSymbol()
                    : "unknown";
                return new DailyValueRow(
                    dv.getId(),
                    symbol,
                    dv.getDate(),
                    dv.getOpen(),
                    dv.getHigh(),
                    dv.getLow(),
                    dv.getClose(),
                    dv.getVolume()
                );
            })
            .toList();

        return new DbDump(sourceRows, assetRows, dailyValueRows);
    }
}
