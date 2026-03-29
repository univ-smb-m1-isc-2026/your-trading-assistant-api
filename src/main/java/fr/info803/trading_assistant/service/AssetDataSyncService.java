package fr.info803.trading_assistant.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import fr.info803.trading_assistant.dto.DailyValueDto;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetDailyValue;
import fr.info803.trading_assistant.entity.AssetSource;
import fr.info803.trading_assistant.repository.AssetDailyValueRepository;
import fr.info803.trading_assistant.repository.AssetRepository;
import fr.info803.trading_assistant.repository.AssetSourceRepository;
import lombok.extern.slf4j.Slf4j;

/*
    Note sur l'injection d'AlertService :
    AssetDataSyncService a besoin d'AlertService pour évaluer les alertes
    après la synchronisation nightly. On l'injecte via le constructeur
    pour maintenir la cohérence avec le reste de l'injection (providers, repos).
    L'évaluation n'est déclenchée QUE dans syncForDate() (scheduler nightly),
    PAS dans syncForDateRange() (chargement historique DevDataInitializer)
    car les alertes portent sur les données du jour, pas sur l'historique.
*/

/*
    Service orchestrateur de la synchronisation quotidienne des données de marché.

    Responsabilités :
      1. Déclencher le fetch de données à minuit chaque nuit via @Scheduled.
      2. Parcourir toutes les AssetSources en base de données.
      3. Pour chaque source, trouver le provider correspondant (Strategy Pattern).
      4. Pour chaque Asset de cette source, appeler le provider et effectuer l'upsert en DB.

    Architecture — pourquoi List<AssetDataProvider> en injection ?
      Spring collecte automatiquement TOUS les beans implémentant AssetDataProvider
      et les injecte sous forme de liste. Ici on les convertit en Map<String, AssetDataProvider>
      pour un accès O(1) par nom de source (ex: "hyperliquid" → HyperliquidAssetDataProvider).
      Cela respecte le principe Open/Closed : ajouter un provider = créer un nouveau bean,
      sans modifier ce service.

    Cron "0 0 0 * * *" — anatomie de l'expression :
      ┌─ secondes (0)
      │ ┌─ minutes (0)
      │ │ ┌─ heures (0 = minuit)
      │ │ │ ┌─ jour du mois (* = tous)
      │ │ │ │ ┌─ mois (* = tous)
      │ │ │ │ │ ┌─ jour de la semaine (* = tous)
      0 0 0 * * *
      → Déclenche à minuit UTC chaque nuit.

    Logique d'upsert (Insert or Update) :
      Pour éviter les doublons si le scheduler est relancé manuellement ou si un
      déploiement se produit juste après minuit, on vérifie d'abord si une entrée
      existe pour (asset, date). Si oui → UPDATE, sinon → INSERT.
*/
@Slf4j
@Service
public class AssetDataSyncService {

    private final AssetSourceRepository assetSourceRepository;
    private final AssetRepository assetRepository;
    private final AssetDailyValueRepository assetDailyValueRepository;
    private final AlertService alertService;
    private final ChartPatternService chartPatternService;

    // Map<sourceName, provider> construite depuis la liste injectée par Spring.
    // Clé = AssetDataProvider.getSourceName() = AssetSource.name en DB.
    private final Map<String, AssetDataProvider> providersByName;

    /*
        Injection de List<AssetDataProvider> et AlertService :
          Spring détecte tous les beans implémentant AssetDataProvider dans le contexte
          et les injecte automatiquement. Actuellement : HyperliquidAssetDataProvider.
          Un 2ème provider (ex: AlphaVantageAssetDataProvider) sera automatiquement
          inclus dès qu'il sera créé et annoté @Component.

          AlertService est injecté pour pouvoir évaluer les alertes actives
          après chaque synchronisation nightly (dans syncForDate()).
    */
    public AssetDataSyncService(
        AssetSourceRepository assetSourceRepository,
        AssetRepository assetRepository,
        AssetDailyValueRepository assetDailyValueRepository,
        AlertService alertService,
        ChartPatternService chartPatternService,
        List<AssetDataProvider> providers
    ) {
        this.assetSourceRepository = assetSourceRepository;
        this.assetRepository = assetRepository;
        this.assetDailyValueRepository = assetDailyValueRepository;
        this.alertService = alertService;
        this.chartPatternService = chartPatternService;
        // Convertit la liste en Map pour un lookup O(1)
        this.providersByName = providers.stream()
            .collect(Collectors.toMap(AssetDataProvider::getSourceName, Function.identity()));

        log.info("AssetDataSyncService initialized with {} provider(s): {}",
            providersByName.size(), providersByName.keySet());
    }

    /*
        Point d'entrée du scheduler nocturne.
        Wrapper minimal : délègue à syncForDate() avec la date J-1.
        Séparé de syncForDate() pour permettre l'appel depuis d'autres composants
        (DevDataInitializer, futurs endpoints admin) avec n'importe quelle date.
    */
    @Scheduled(cron = "0 1 1 * * *")
    public void syncDailyPrices() {
        // J-1 car à minuit, la journée qui vient de se terminer est celle qu'on veut stocker.
        syncForDate(LocalDate.now().minusDays(1));
    }

    /*
        Synchronise toutes les sources/assets pour une date donnée.

        Wrapper autour de fetchDailyValues(symbol, startDate, endDate, apiUrl) :
        passe startDate == endDate == targetDate pour ne récupérer qu'un seul jour.
        C'est la méthode utilisée par le scheduler nocturne et l'ancienne boucle
        du DevDataInitializer.

        Flux d'exécution :
          1. Charge toutes les AssetSources depuis la DB.
          2. Pour chaque source, vérifie qu'un provider existe.
          3. Charge tous les assets de cette source.
          4. Pour chaque asset, appelle le provider (startDate == endDate) et upsert la valeur en DB.

        Visibility: public pour permettre les appels cross-package depuis DevDataInitializer
        et les futurs endpoints admin. Testable via Mockito.spy().
    */
    public void syncForDate(LocalDate targetDate) {
        log.info("Starting daily price sync for date={}", targetDate);

        List<AssetSource> sources = assetSourceRepository.findAll();

        if (sources.isEmpty()) {
            log.warn("No AssetSource found in database. Add sources and assets to enable sync.");
        } else {
            int totalSynced = 0;
            int totalFailed = 0;

            for (AssetSource source : sources) {
                AssetDataProvider provider = providersByName.get(source.getName());

                if (provider == null) {
                    log.warn("No provider registered for source='{}'. Skipping.", source.getName());
                    continue;
                }

                List<Asset> assets = assetRepository.findBySource(source);
                log.info("Syncing {} asset(s) from source='{}'", assets.size(), source.getName());

                for (Asset asset : assets) {
                    try {
                        // Anti-rate limit for Yahoo
                        if (source.getName().equals("yahoo")) {
                            Thread.sleep(500);
                        }

                        // Wrapper : startDate == endDate == targetDate → une seule bougie
                        List<DailyValueDto> values = provider.fetchDailyValues(
                            asset.getSymbol(), targetDate, targetDate, source.getUrl()
                        );

                        for (DailyValueDto dto : values) {
                            upsertDailyValue(asset, dto);
                            totalSynced++;
                        }

                        if (values.isEmpty()) {
                            log.warn("No data returned for asset='{}' date={}", asset.getSymbol(), targetDate);
                            totalFailed++;
                        }

                    } catch (Exception e) {
                        // On ne stoppe pas le sync pour les autres assets en cas d'erreur sur un seul.
                        log.error("Failed to sync asset='{}': {}", asset.getSymbol(), e.getMessage());
                        totalFailed++;
                    }
                }
            }

            log.info("Daily price sync completed. synced={} failed={} date={}", totalSynced, totalFailed, targetDate);
        }

        // Évalue les alertes actives contre les bougies qui viennent d'être synchronisées.
        // Placé APRÈS le sync pour s'assurer que toutes les bougies sont en DB avant évaluation.
        // Exécuté même si aucune source n'existe : des alertes peuvent porter sur des assets
        // dont les données ont été synchronisées précédemment (ex: via syncForDateRange).
        // Si le sync a échoué pour certains assets, les alertes correspondantes seront
        // simplement ignorées (pas de bougie → pas d'évaluation, voir AlertService.evaluateSingleAlert).
        alertService.evaluateAlerts(targetDate);
        
        // Evalue les figures chartistes
        chartPatternService.evaluatePatterns(targetDate);
    }

    /*
        Synchronise toutes les sources/assets pour un intervalle de dates en un seul appel
        API par asset — beaucoup plus efficace que d'appeler syncForDate() en boucle.

        Utilisée par DevDataInitializer pour charger 1 an d'historique au démarrage :
          syncForDateRange(LocalDate.now().minusDays(365), LocalDate.now().minusDays(1))
        → 1 appel HTTP par asset au lieu de 365.

        Flux d'exécution :
          1. Charge toutes les AssetSources depuis la DB.
          2. Pour chaque source, vérifie qu'un provider existe.
          3. Charge tous les assets de cette source.
          4. Pour chaque asset, appelle le provider avec l'intervalle complet.
          5. Upsert chaque bougie retournée en DB.

        Visibility: public pour permettre les appels cross-package (DevDataInitializer,
        futurs endpoints admin).
    */
    public void syncForDateRange(LocalDate startDate, LocalDate endDate) {
        log.info("Starting bulk price sync for range [{} → {}]", startDate, endDate);

        List<AssetSource> sources = assetSourceRepository.findAll();

        if (sources.isEmpty()) {
            log.warn("No AssetSource found in database. Add sources and assets to enable sync.");
            return;
        }

        int totalSynced = 0;
        int totalFailed = 0;

        for (AssetSource source : sources) {
            AssetDataProvider provider = providersByName.get(source.getName());

            if (provider == null) {
                log.warn("No provider registered for source='{}'. Skipping.", source.getName());
                continue;
            }

            List<Asset> assets = assetRepository.findBySource(source);
            log.info("Bulk syncing {} asset(s) from source='{}' for range [{} → {}]",
                assets.size(), source.getName(), startDate, endDate);

            for (Asset asset : assets) {
                try {
                    // Anti-rate limit for Yahoo
                    if (source.getName().equals("yahoo")) {
                        Thread.sleep(500);
                    }

                    List<DailyValueDto> values = provider.fetchDailyValues(
                        asset.getSymbol(), startDate, endDate, source.getUrl()
                    );

                    for (DailyValueDto dto : values) {
                        upsertDailyValue(asset, dto);
                        totalSynced++;
                    }

                    if (values.isEmpty()) {
                        log.warn("No data returned for asset='{}' range=[{} → {}]",
                            asset.getSymbol(), startDate, endDate);
                        totalFailed++;
                    } else {
                        log.info("Synced {} candle(s) for asset='{}'", values.size(), asset.getSymbol());
                    }

                } catch (Exception e) {
                    log.error("Failed to sync asset='{}': {}", asset.getSymbol(), e.getMessage());
                    totalFailed++;
                }
            }
        }

        log.info("Bulk price sync completed. synced={} failed={} range=[{} → {}]",
            totalSynced, totalFailed, startDate, endDate);
    }

    /*
        Logique d'upsert : INSERT si inexistant, UPDATE si déjà présent.

        La contrainte d'unicité (asset_id, date) en base de données sert de filet
        de sécurité supplémentaire contre les doublons en cas de conditions de course.

        Visibility: package-private pour permettre les tests unitaires via Mockito.spy()
        sans passer par le scheduler complet.
    */
    // package-private for testability via Mockito.spy()
    void upsertDailyValue(Asset asset, DailyValueDto dto) {
        Optional<AssetDailyValue> existing = assetDailyValueRepository.findByAssetAndDate(asset, dto.getDate());

        if (existing.isPresent()) {
            // UPDATE : on met à jour tous les champs OHLCV
            AssetDailyValue value = existing.get();
            value.setOpen(dto.getOpen());
            value.setHigh(dto.getHigh());
            value.setLow(dto.getLow());
            value.setClose(dto.getClose());
            value.setVolume(dto.getVolume());
            assetDailyValueRepository.save(value);
            log.debug("Updated AssetDailyValue for asset='{}' date={}", asset.getSymbol(), dto.getDate());
        } else {
            // INSERT : nouvelle entrée pour ce (asset, date)
            assetDailyValueRepository.save(
                AssetDailyValue.builder()
                    .asset(asset)
                    .date(dto.getDate())
                    .open(dto.getOpen())
                    .high(dto.getHigh())
                    .low(dto.getLow())
                    .close(dto.getClose())
                    .volume(dto.getVolume())
                    .build()
            );
            log.debug("Inserted AssetDailyValue for asset='{}' date={}", asset.getSymbol(), dto.getDate());
        }
    }
}
