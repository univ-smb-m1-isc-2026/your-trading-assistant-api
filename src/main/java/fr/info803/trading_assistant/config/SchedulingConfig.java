package fr.info803.trading_assistant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/*
    Configuration du scheduling Spring.

    @EnableScheduling active le support de l'annotation @Scheduled dans tout le
    contexte Spring. Sans cette annotation, les méthodes @Scheduled (comme
    AssetDataSyncService.syncDailyPrices) seraient ignorées au démarrage.

    Pourquoi une classe dédiée plutôt qu'annoter la classe principale ?
      - Principe de responsabilité unique (SRP) : chaque classe de config gère
        une seule préoccupation (scheduling, security, CORS, etc.).
      - Cohérent avec le pattern existant du projet (SecurityConfig, CorsConfig,
        ApplicationConfig sont déjà séparés).
      - Facilite les tests : on peut désactiver le scheduling dans les tests
        d'intégration en excluant cette config via @SpringBootTest(excludeConfigs...).

    Le scheduler de Spring s'exécute par défaut sur un thread pool de taille 1.
    Cela signifie que si syncDailyPrices() prend plus d'une minute, le prochain
    déclenchement attendra la fin de l'exécution en cours (pas de déclenchements
    parallèles par défaut)
*/
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Classe vide intentionnellement : @EnableScheduling et @Configuration
    // sont les seules annotations nécessaires pour activer le scheduling.
}
