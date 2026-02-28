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

/*
    Initialiseur de données pour le profil de développement uniquement.

    Pourquoi ApplicationRunner et non @PostConstruct ou CommandLineRunner ?
      - ApplicationRunner.run() est appelé APRÈS que le contexte Spring est entièrement
        prêt (tous les beans initialisés, toutes les connexions DB établies).
      - @PostConstruct s'exécute pendant la construction du bean, avant que JPA soit
        pleinement opérationnel — risque de NPE sur les repositories.
      - CommandLineRunner est équivalent mais ApplicationRunner reçoit un objet
        ApplicationArguments typé (plus propre si on veut lire des args CLI plus tard).

    Pourquoi @Profile("dev") ?
      - Ce bean n'est instancié que si le profil actif est "dev".
      - En production (profil "prod"), Spring ne crée pas ce bean → aucun risque
        d'écraser des données réelles.
      - La H2 in-memory est de toute façon vide à chaque redémarrage, donc
        ce bean est le seul moyen de l'amorcer avec des données cohérentes.

    Idempotence :
      - On vérifie si la source "hyperliquid" existe déjà avant d'insérer.
      - Protection utile si on remplace un jour H2 par une DB persistante en dev
        (ex: H2 fichier, ou PostgreSQL local).
*/
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataInitializer implements ApplicationRunner {

    private static final String SOURCE_NAME = "hyperliquid";
    private static final String SOURCE_URL  = "https://api.hyperliquid.xyz/info";

    private static final List<String> SYMBOLS = List.of("BTC", "ETH", "AERO", "SAGA", "MANTA");

    private final AssetSourceRepository assetSourceRepository;
    private final AssetRepository assetRepository;
    private final AssetDataSyncService assetDataSyncService;

    /*
        Point d'entrée : appelé une seule fois au démarrage, après que Spring Boot
        a fini d'initialiser le contexte.

        Flux :
          1. Garde idempotente — si les données existent déjà, on ne fait rien.
          2. Création de l'AssetSource "hyperliquid".
          3. Création des 5 assets liés à cette source.
          4. Appel de syncDailyPrices() → fetch HTTP réel vers Hyperliquid,
             upsert des valeurs OHLCV pour J-1 dans AssetDailyValue.
    */
    @Override
    public void run(ApplicationArguments args) {
        if (assetSourceRepository.findByName(SOURCE_NAME).isPresent()) {
            log.info("[DevDataInitializer] Data already present, skipping initialization.");
            return;
        }

        log.info("[DevDataInitializer] Initializing dev data...");

        // Étape 1 — Création de la source Hyperliquid
        // On sauvegarde d'abord la source pour obtenir l'id généré par la séquence JPA,
        // nécessaire pour la foreign key dans Asset.
        AssetSource source = assetSourceRepository.save(
            AssetSource.builder()
                .name(SOURCE_NAME)
                .url(SOURCE_URL)
                .build()
        );
        log.info("[DevDataInitializer] Created AssetSource: id={} name={}", source.getId(), source.getName());

        // Étape 2 — Création des assets
        // Stream + map pour éviter la répétition, toList() retourne une liste immuable.
        List<Asset> assets = SYMBOLS.stream()
            .map(symbol -> Asset.builder()
                .symbol(symbol)
                .source(source)
                .build())
            .toList();

        assetRepository.saveAll(assets);
        log.info("[DevDataInitializer] Created {} assets: {}", assets.size(), SYMBOLS);

        // Étape 3 — Synchronisation de 1 an d'historique en bulk
        // syncForDateRange() appelle l'API une seule fois par asset avec un intervalle
        // de 365 jours, au lieu de boucler jour par jour (365 appels × N assets → N appels).
        // L'API Hyperliquid supporte nativement les intervalles via startTime/endTime.
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = LocalDate.now().minusDays(365);
        log.info("[DevDataInitializer] Triggering bulk sync for 1 year of history [{} → {}]...",
            startDate, endDate);
        assetDataSyncService.syncForDateRange(startDate, endDate);

        log.info("[DevDataInitializer] Dev initialization complete.");
    }
}
