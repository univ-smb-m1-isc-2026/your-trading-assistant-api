package fr.info803.trading_assistant.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.Alert;
import fr.info803.trading_assistant.entity.AlertDirection;
import fr.info803.trading_assistant.entity.AlertType;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetSource;
import fr.info803.trading_assistant.entity.Role;
import fr.info803.trading_assistant.entity.TriggeredAlert;
import fr.info803.trading_assistant.repository.AccountRepository;
import fr.info803.trading_assistant.repository.AlertRepository;
import fr.info803.trading_assistant.repository.AssetRepository;
import fr.info803.trading_assistant.repository.AssetSourceRepository;
import fr.info803.trading_assistant.repository.TriggeredAlertRepository;
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
    private final AccountRepository accountRepository;
    private final AlertRepository alertRepository;
    private final TriggeredAlertRepository triggeredAlertRepository;
    private final PasswordEncoder passwordEncoder;

    /*
        Point d'entrée : appelé une seule fois au démarrage, après que Spring Boot
        a fini d'initialiser le contexte.

        Flux :
          1. Garde idempotente — si les données existent déjà, on ne fait rien.
          2. Création de l'AssetSource "hyperliquid".
          3. Création des 5 assets liés à cette source.
          4. Appel de syncForDateRange() → fetch HTTP réel vers Hyperliquid,
             upsert des valeurs OHLCV pour 1 an dans AssetDailyValue.
          5. Création du compte demo avec alertes et historique de déclenchements.
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

        // Étape 4 — Injection du compte demo avec alertes et historique
        seedDemoUser();

        log.info("[DevDataInitializer] Dev initialization complete.");
    }

    /*
        Crée un utilisateur demo avec :
          - 3 alertes configurées (2 actives, 1 one-shot consommée)
          - 5 triggered alerts dont 2 déclenchées aujourd'hui

        Pourquoi séparer en méthode privée ?
          - Lisibilité : run() reste un flux haut niveau ("quelles étapes"),
            seedDemoUser() contient les détails ("comment").
          - Testabilité future : on pourrait appeler cette méthode indépendamment.

        Données injectées :
          Alert 1 — BTC PRICE_THRESHOLD ABOVE 80 000   (recurring, active)
          Alert 2 — BTC VOLUME_THRESHOLD ABOVE 5B      (recurring, active)
          Alert 3 — ETH PRICE_THRESHOLD BELOW 2 000    (one-shot, inactive car déjà déclenchée)

          TA 1 — Alert1, J-30, prix 82 000       (passé)
          TA 2 — Alert1, J-15, prix 85 500       (passé)
          TA 3 — Alert3, J-45, prix 1 950        (passé — a rendu Alert3 inactive)
          TA 4 — Alert1, aujourd'hui, prix 88 000 (AUJOURD'HUI)
          TA 5 — Alert2, aujourd'hui, vol 5.2B   (AUJOURD'HUI)
    */
    private void seedDemoUser() {
        if (accountRepository.existsByEmail("demo@example.com")) {
            log.info("[DevDataInitializer] Demo user already present, skipping.");
            return;
        }

        log.info("[DevDataInitializer] Seeding demo user with alerts and triggered history...");

        // --- Compte demo ---
        // Le mot de passe est hashé via BCrypt (même encodeur que lors d'un vrai register).
        // Sans ce hashage, Spring Security rejetterait toute tentative de login.
        Account demo = accountRepository.save(
            Account.builder()
                .username("demouser")
                .email("demo@example.com")
                .password(passwordEncoder.encode("demo123"))
                .role(Role.ROLE_USER)
                .build()
        );
        log.info("[DevDataInitializer] Created demo account: id={} email={}", demo.getId(), demo.getEmail());

        // --- Assets de référence ---
        // Les assets ont été créés et persistés à l'étape 2, on les recharge par symbole.
        Asset btc = assetRepository.findBySymbol("BTC")
            .orElseThrow(() -> new IllegalStateException("Asset BTC introuvable — vérifiez l'étape de sync"));
        Asset eth = assetRepository.findBySymbol("ETH")
            .orElseThrow(() -> new IllegalStateException("Asset ETH introuvable — vérifiez l'étape de sync"));

        // Date de création commune aux alertes (il y a 60 jours)
        LocalDateTime alertCreatedAt = LocalDateTime.now().minusDays(60);

        // --- Alert 1 : BTC dépasse 80 000 (récurrente, toujours active) ---
        // recurring=true : l'alerte se réarme après chaque déclenchement.
        Alert alert1 = alertRepository.save(
            Alert.builder()
                .account(demo)
                .asset(btc)
                .type(AlertType.PRICE_THRESHOLD)
                .direction(AlertDirection.ABOVE)
                .thresholdValue(new BigDecimal("80000"))
                .recurring(true)
                .active(true)
                .createdAt(alertCreatedAt)
                .build()
        );

        // --- Alert 2 : volume BTC dépasse 5 milliards (récurrente, toujours active) ---
        Alert alert2 = alertRepository.save(
            Alert.builder()
                .account(demo)
                .asset(btc)
                .type(AlertType.VOLUME_THRESHOLD)
                .direction(AlertDirection.ABOVE)
                .thresholdValue(new BigDecimal("5000000000"))
                .recurring(true)
                .active(true)
                .createdAt(alertCreatedAt)
                .build()
        );

        // --- Alert 3 : ETH passe sous 2 000 (one-shot, déjà consommée → inactive) ---
        // recurring=false + active=false reflète le comportement du AlertEvaluator :
        // après un premier déclenchement, l'alerte est automatiquement désactivée.
        Alert alert3 = alertRepository.save(
            Alert.builder()
                .account(demo)
                .asset(eth)
                .type(AlertType.PRICE_THRESHOLD)
                .direction(AlertDirection.BELOW)
                .thresholdValue(new BigDecimal("2000"))
                .recurring(false)
                .active(false)
                .createdAt(alertCreatedAt)
                .build()
        );

        log.info("[DevDataInitializer] Created 3 alerts for demo user (ids: {}, {}, {}).",
            alert1.getId(), alert2.getId(), alert3.getId());

        // --- 5 triggered alerts ---
        // candleDate : date de la bougie qui a déclenché l'alerte (clé métier).
        // triggeredAt : horodatage du moment où l'évaluateur a traité le déclenchement.
        // Pour les bougie passées, triggeredAt = minuit de la date (heure du scheduler nightly).
        // Pour aujourd'hui, triggeredAt = maintenant (simulation d'un run temps réel).
        //
        // Note contrainte unique : (alert_id, candle_date) → une alerte ne peut se déclencher
        // qu'une seule fois par jour de bougie, protection anti-doublon en DB.
        LocalDate today = LocalDate.now();

        triggeredAlertRepository.saveAll(List.of(

            // TA 1 — Alert1 déclenchée il y a 30 jours, BTC high à 82 000
            TriggeredAlert.builder()
                .alert(alert1)
                .triggeredValue(new BigDecimal("82000"))
                .candleDate(today.minusDays(30))
                .triggeredAt(today.minusDays(30).atTime(2, 0))
                .build(),

            // TA 2 — Alert1 déclenchée il y a 15 jours, BTC high à 85 500
            TriggeredAlert.builder()
                .alert(alert1)
                .triggeredValue(new BigDecimal("85500"))
                .candleDate(today.minusDays(15))
                .triggeredAt(today.minusDays(15).atTime(2, 0))
                .build(),

            // TA 3 — Alert3 (ETH one-shot) déclenchée il y a 45 jours, ETH low à 1 950
            //         Ce déclenchement a rendu Alert3 inactive (recurring=false).
            TriggeredAlert.builder()
                .alert(alert3)
                .triggeredValue(new BigDecimal("1950"))
                .candleDate(today.minusDays(45))
                .triggeredAt(today.minusDays(45).atTime(2, 0))
                .build(),

            // TA 4 — Alert1 déclenchée AUJOURD'HUI, BTC high à 88 000
            TriggeredAlert.builder()
                .alert(alert1)
                .triggeredValue(new BigDecimal("88000"))
                .candleDate(today)
                .triggeredAt(LocalDateTime.now())
                .build(),

            // TA 5 — Alert2 (volume) déclenchée AUJOURD'HUI, volume à 5.2 milliards
            TriggeredAlert.builder()
                .alert(alert2)
                .triggeredValue(new BigDecimal("5200000000"))
                .candleDate(today)
                .triggeredAt(LocalDateTime.now())
                .build()
        ));

        log.info("[DevDataInitializer] Created 5 triggered alerts (2 today) for demo user.");
    }
}
