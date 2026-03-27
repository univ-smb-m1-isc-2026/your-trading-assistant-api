package fr.info803.trading_assistant.config;

import org.springframework.context.annotation.Configuration;

import fr.info803.trading_assistant.repository.AccountFavoriteAssetRepository;
import fr.info803.trading_assistant.repository.AccountRepository;
import fr.info803.trading_assistant.repository.AlertRepository;
import fr.info803.trading_assistant.repository.TriggeredAlertRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration that registers custom application metrics (Gauges) into the Micrometer MeterRegistry.
 * These metrics will automatically be exposed to Prometheus under the /actuator/prometheus endpoint.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class AppMetricsConfig {

    private final MeterRegistry meterRegistry;
    private final AccountRepository accountRepository;
    private final AlertRepository alertRepository;
    private final AccountFavoriteAssetRepository favoriteRepository;
    private final TriggeredAlertRepository triggeredAlertRepository;

    @PostConstruct
    public void registerMetrics() {
        log.info("Registering custom application metrics in MeterRegistry");

        Gauge.builder("trading_assistant.users.count", accountRepository, AccountRepository::count)
             .description("The total number of registered users")
             .register(meterRegistry);

        Gauge.builder("trading_assistant.alerts.count", alertRepository, AlertRepository::count)
             .description("The total number of configured alerts")
             .register(meterRegistry);

        Gauge.builder("trading_assistant.favorites.count", favoriteRepository, AccountFavoriteAssetRepository::count)
             .description("The total number of favorite assets saved by users")
             .register(meterRegistry);

        Gauge.builder("trading_assistant.triggered_alerts.count", triggeredAlertRepository, TriggeredAlertRepository::count)
             .description("The total number of alerts that have been triggered")
             .register(meterRegistry);
    }
}
