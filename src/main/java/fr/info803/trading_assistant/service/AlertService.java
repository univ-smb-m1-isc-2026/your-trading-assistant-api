package fr.info803.trading_assistant.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.info803.trading_assistant.dto.AlertResponse;
import fr.info803.trading_assistant.dto.CreateAlertRequest;
import fr.info803.trading_assistant.dto.TriggeredAlertResponse;
import fr.info803.trading_assistant.dto.UpdateAlertRequest;
import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.Alert;
import fr.info803.trading_assistant.entity.AlertDirection;
import fr.info803.trading_assistant.entity.AlertType;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetDailyValue;
import fr.info803.trading_assistant.entity.TriggeredAlert;
import fr.info803.trading_assistant.event.AlertCreatedEvent;
import fr.info803.trading_assistant.event.AlertsTriggeredEvent;
import fr.info803.trading_assistant.exception.AlertNotFoundException;
import fr.info803.trading_assistant.exception.AssetNotFoundException;
import fr.info803.trading_assistant.repository.AccountRepository;
import fr.info803.trading_assistant.repository.AlertRepository;
import fr.info803.trading_assistant.repository.AssetDailyValueRepository;
import fr.info803.trading_assistant.repository.AssetRepository;
import fr.info803.trading_assistant.repository.TriggeredAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
    Service métier pour le système d'alertes.

    Responsabilités :
    1. CRUD des alertes configurées par l'utilisateur.
    2. Évaluation des alertes actives contre les bougies quotidiennes (appelé
       par AssetDataSyncService après le sync nightly).

    Architecture — Strategy Pattern pour l'évaluation :
    Spring injecte automatiquement tous les beans implémentant AlertEvaluator
    dans la List<AlertEvaluator>. AlertService ne connaît PAS les types d'alertes
    spécifiques — il délègue via supports() + evaluate().
    Ajouter un nouveau type d'alerte = créer un nouveau @Component AlertEvaluator.
    Aucune modification de ce service nécessaire (Open/Closed Principle).

    Sécurité — isolation des données par utilisateur :
    Toutes les opérations de lecture/modification vérifient que l'alerte
    appartient bien à l'utilisateur authentifié via findByIdAndAccount().
    Un utilisateur ne peut jamais accéder aux alertes d'un autre.
*/
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AccountRepository accountRepository;
    private final AssetRepository assetRepository;
    private final AlertRepository alertRepository;
    private final TriggeredAlertRepository triggeredAlertRepository;
    private final AssetDailyValueRepository assetDailyValueRepository;
    private final List<AlertEvaluator> evaluators;
    private final ApplicationEventPublisher eventPublisher;

    /*
        Charge l'utilisateur depuis la base.
        Même pattern que FavoriteService.loadAccount().
    */
    private Account loadAccount(String email) {
        return accountRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("Account not found: " + email));
    }

    /*
        Convertit une entité Alert en DTO AlertResponse.
        Dénormalise le symbol depuis Alert.asset.symbol pour éviter
        au client de faire une requête supplémentaire.
    */
    private AlertResponse toAlertResponse(Alert alert) {
        return AlertResponse.builder()
            .id(alert.getId())
            .symbol(alert.getAsset().getSymbol())
            .type(alert.getType().name())
            .direction(alert.getDirection().name())
            .thresholdValue(alert.getThresholdValue())
            .shortPeriod(alert.getShortPeriod())
            .longPeriod(alert.getLongPeriod())
            .maType(alert.getMaType())
            .recurring(alert.isRecurring())
            .active(alert.isActive())
            .createdAt(alert.getCreatedAt())
            .build();
    }

    /*
        Convertit une entité TriggeredAlert en DTO TriggeredAlertResponse.
        Dénormalise les informations de configuration depuis Alert pour
        fournir un contexte complet au client.
    */
    private TriggeredAlertResponse toTriggeredAlertResponse(TriggeredAlert triggered) {
        Alert alert = triggered.getAlert();
        return TriggeredAlertResponse.builder()
            .id(triggered.getId())
            .alertId(alert.getId())
            .symbol(alert.getAsset().getSymbol())
            .type(alert.getType().name())
            .direction(alert.getDirection().name())
            .thresholdValue(alert.getThresholdValue())
            .triggeredValue(triggered.getTriggeredValue())
            .candleDate(triggered.getCandleDate())
            .triggeredAt(triggered.getTriggeredAt())
            .build();
    }

    // ─────────────────────────────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────────────────────────────

    /*
        Retourne toutes les alertes configurées par l'utilisateur.
        Inclut les alertes actives ET inactives pour permettre au client
        d'afficher l'historique de configuration et de réactiver des alertes.
    */
    public List<AlertResponse> getAlerts(String email) {
        log.info("Fetching alerts for account={}", email);
        Account account = loadAccount(email);
        List<Alert> alerts = alertRepository.findByAccount(account);
        log.info("Fetched {} alert(s) for account={}", alerts.size(), email);
        return alerts.stream().map(this::toAlertResponse).toList();
    }

    /*
        Retourne l'historique des alertes déclenchées pour l'utilisateur.
        Trié par date de déclenchement décroissant (le plus récent en premier).
    */
    public List<TriggeredAlertResponse> getTriggeredAlerts(String email) {
        log.info("Fetching triggered alerts for account={}", email);
        Account account = loadAccount(email);
        List<TriggeredAlert> triggered = triggeredAlertRepository
            .findByAlertAccountOrderByTriggeredAtDesc(account);
        log.info("Fetched {} triggered alert(s) for account={}", triggered.size(), email);
        return triggered.stream().map(this::toTriggeredAlertResponse).toList();
    }

    /*
        Crée une nouvelle alerte.

        Flux :
          1. Charge le compte via email.
          2. Résout le symbol en Asset (ou 404).
          3. Parse le type et la direction depuis les strings du DTO.
          4. Construit l'entité Alert avec active=true et createdAt=now.
          5. Sauvegarde et retourne la réponse.

        Validation des enums :
          On utilise AlertType.valueOf() et AlertDirection.valueOf() pour
          convertir les strings. Si la valeur est invalide, Java lève
          IllegalArgumentException — traitée par le catch ci-dessous.
    */
    public AlertResponse createAlert(String email, CreateAlertRequest request) {
        log.info("Creating alert symbol={} type={} direction={} for account={}",
            request.getSymbol(), request.getType(), request.getDirection(), email);

        Account account = loadAccount(email);

        Asset asset = assetRepository.findBySymbol(request.getSymbol())
            .orElseThrow(() -> new AssetNotFoundException(request.getSymbol()));

        AlertType type = parseAlertType(request.getType());
        AlertDirection direction = parseAlertDirection(request.getDirection());

        // Validation conditionnelle selon le type d'alerte
        if (type == AlertType.MA_CROSSOVER) {
            validateMaCrossoverFields(request);
        } else {
            if (request.getThresholdValue() == null) {
                throw new IllegalArgumentException(
                    "thresholdValue is required for alert type " + type);
            }
        }

        Alert alert = Alert.builder()
            .account(account)
            .asset(asset)
            .type(type)
            .direction(direction)
            .thresholdValue(request.getThresholdValue())
            .shortPeriod(request.getShortPeriod())
            .longPeriod(request.getLongPeriod())
            .maType(request.getMaType())
            .recurring(request.getRecurring())
            .active(true)
            .createdAt(LocalDateTime.now())
            .build();

        alert = alertRepository.save(alert);
        log.info("Created alert id={} for account={}", alert.getId(), email);
        
        // Notification Discord
        eventPublisher.publishEvent(new AlertCreatedEvent(alert, email));
        
        return toAlertResponse(alert);
    }

    /*
        Modifie une alerte existante.

        Seuls les champs non-null du DTO sont appliqués (mise à jour partielle).
        Cela permet au client d'envoyer uniquement les champs à modifier :
          { "thresholdValue": 105000 } → ne modifie que le seuil.
          { "active": true }           → réactive une alerte one-shot.

        Sécurité : findByIdAndAccount vérifie que l'alerte appartient au compte.
    */
    public AlertResponse updateAlert(String email, Long alertId, UpdateAlertRequest request) {
        log.info("Updating alert id={} for account={}", alertId, email);

        Account account = loadAccount(email);

        Alert alert = alertRepository.findByIdAndAccount(alertId, account)
            .orElseThrow(() -> new AlertNotFoundException(alertId));

        if (request.getType() != null) {
            alert.setType(parseAlertType(request.getType()));
        }
        if (request.getDirection() != null) {
            alert.setDirection(parseAlertDirection(request.getDirection()));
        }
        if (request.getThresholdValue() != null) {
            alert.setThresholdValue(request.getThresholdValue());
        }
        if (request.getRecurring() != null) {
            alert.setRecurring(request.getRecurring());
        }
        if (request.getActive() != null) {
            alert.setActive(request.getActive());
        }
        if (request.getShortPeriod() != null) {
            alert.setShortPeriod(request.getShortPeriod());
        }
        if (request.getLongPeriod() != null) {
            alert.setLongPeriod(request.getLongPeriod());
        }
        if (request.getMaType() != null) {
            alert.setMaType(request.getMaType());
        }

        alert = alertRepository.save(alert);
        log.info("Updated alert id={} for account={}", alertId, email);
        return toAlertResponse(alert);
    }

    /*
        Supprime une alerte et tout son historique de déclenchements.

        @Transactional requis :
        - deleteByAlert() fait un SELECT + DELETE (Spring Data JPA pattern).
        - Les deux suppressions (triggered alerts + alert) doivent être atomiques.
        - Sans transaction, on risque un état incohérent si la suppression des
          triggered alerts réussit mais pas celle de l'alert.

        Sécurité : findByIdAndAccount vérifie que l'alerte appartient au compte.
    */
    @Transactional
    public void deleteAlert(String email, Long alertId) {
        log.info("Deleting alert id={} for account={}", alertId, email);

        Account account = loadAccount(email);

        Alert alert = alertRepository.findByIdAndAccount(alertId, account)
            .orElseThrow(() -> new AlertNotFoundException(alertId));

        // Supprime d'abord l'historique (FK vers alert)
        triggeredAlertRepository.deleteByAlert(alert);
        alertRepository.delete(alert);
        log.info("Deleted alert id={} and its triggered history for account={}", alertId, email);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Évaluation
    // ─────────────────────────────────────────────────────────────────────

    /*
        Évalue toutes les alertes actives contre les bougies d'une date donnée.

        Appelé par AssetDataSyncService.syncDailyPrices() après le sync nightly.

        Flux :
          1. Charge toutes les alertes actives (tous utilisateurs confondus).
          2. Pour chaque alerte, récupère la bougie du jour pour l'asset.
          3. Trouve l'évaluateur approprié via supports().
          4. Appelle evaluate() : si Optional.present → condition satisfaite.
          5. Vérifie l'anti-doublon (existsByAlertAndCandleDate).
          6. Crée un TriggeredAlert.
          7. Si one-shot (recurring=false) → désactive l'alerte.

        Résilience :
        - Si une alerte lève une exception, on log et on continue avec les autres.
        - Si aucun évaluateur ne supporte le type, on log un warning et on skip.
        - Si aucune bougie n'existe pour l'asset à cette date, on skip.
    */
   @Transactional
    public void evaluateAlerts(LocalDate date) {
        log.info("Evaluating alerts for date={}", date);

        List<Alert> activeAlerts = alertRepository.findByActiveTrue();

        if (activeAlerts.isEmpty()) {
            log.info("No active alerts to evaluate");
            return;
        }

        List<TriggeredAlert> newlyTriggered = new java.util.ArrayList<>();
        int skipped = 0;

        for (Alert alert : activeAlerts) {
            try {
                Optional<TriggeredAlert> result = evaluateSingleAlert(alert, date);
                result.ifPresent(newlyTriggered::add);
            } catch (Exception e) {
                log.error("Failed to evaluate alert id={}: {}", alert.getId(), e.getMessage());
                skipped++;
            }
        }

        if (!newlyTriggered.isEmpty()) {
            eventPublisher.publishEvent(new AlertsTriggeredEvent(newlyTriggered, date));
        }

        log.info("Alert evaluation completed for date={}: triggered={} skipped={}",
            date, newlyTriggered.size(), skipped);
    }

    /*
        Évalue une seule alerte contre la bougie du jour.
        Retourne l'objet TriggeredAlert si l'alerte a été déclenchée, Optional.empty() sinon.

        Visibility: package-private pour testabilité via Mockito.spy().
    */
    // package-private for testability via Mockito.spy()
    Optional<TriggeredAlert> evaluateSingleAlert(Alert alert, LocalDate date) {
        // 1. Trouver l'évaluateur pour ce type d'alerte
        Optional<AlertEvaluator> evaluatorOpt = evaluators.stream()
            .filter(e -> e.supports(alert.getType()))
            .findFirst();

        if (evaluatorOpt.isEmpty()) {
            log.warn("No evaluator found for alert type={}", alert.getType());
            return Optional.empty();
        }

        // 2. Récupérer la bougie du jour pour l'asset de l'alerte
        Optional<AssetDailyValue> candleOpt = assetDailyValueRepository
            .findByAssetAndDate(alert.getAsset(), date);

        if (candleOpt.isEmpty()) {
            log.debug("No candle for asset='{}' date={}, skipping alert id={}",
                alert.getAsset().getSymbol(), date, alert.getId());
            return Optional.empty();
        }

        // 3. Évaluer la condition
        Optional<BigDecimal> result = evaluatorOpt.get().evaluate(alert, candleOpt.get());

        if (result.isEmpty()) {
            return Optional.empty();
        }

        // 4. Anti-doublon : ne pas déclencher deux fois pour la même date
        if (triggeredAlertRepository.existsByAlertAndCandleDate(alert, date)) {
            log.debug("Alert id={} already triggered for date={}, skipping", alert.getId(), date);
            return Optional.empty();
        }

        // 5. Créer le déclenchement
        TriggeredAlert triggeredAlert = TriggeredAlert.builder()
            .alert(alert)
            .triggeredValue(result.get())
            .candleDate(date)
            .triggeredAt(LocalDateTime.now())
            .build();

        triggeredAlert = triggeredAlertRepository.save(triggeredAlert);
        log.info("Alert id={} triggered for asset='{}' date={} value={}",
            alert.getId(), alert.getAsset().getSymbol(), date, result.get());

        // 6. Si one-shot → désactiver l'alerte
        if (!alert.isRecurring()) {
            alert.setActive(false);
            alertRepository.save(alert);
            log.info("One-shot alert id={} deactivated", alert.getId());
        }

        return Optional.of(triggeredAlert);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Utilitaires
    // ─────────────────────────────────────────────────────────────────────

    /*
        Parse le type d'alerte depuis un string.
        Lève IllegalArgumentException si la valeur est invalide.
    */
    // package-private for testability via Mockito.spy()
    AlertType parseAlertType(String type) {
        try {
            return AlertType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid alert type: " + type
                + ". Valid values: " + java.util.Arrays.toString(AlertType.values()));
        }
    }

    /*
        Parse la direction d'alerte depuis un string.
        Lève IllegalArgumentException si la valeur est invalide.
    */
    // package-private for testability via Mockito.spy()
    AlertDirection parseAlertDirection(String direction) {
        try {
            return AlertDirection.valueOf(direction);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid alert direction: " + direction
                + ". Valid values: " + java.util.Arrays.toString(AlertDirection.values()));
        }
    }

    /*
        Valide les champs spécifiques aux alertes MA_CROSSOVER.

        Règles :
        - shortPeriod et longPeriod sont obligatoires.
        - Les deux périodes doivent être >= 1.
        - shortPeriod doit être strictement inférieur à longPeriod
          (sinon le croisement n'a aucun sens financier).
        - maType est obligatoire et doit être "SMA" ou "EMA".
    */
    // package-private for testability via Mockito.spy()
    void validateMaCrossoverFields(CreateAlertRequest request) {
        if (request.getShortPeriod() == null || request.getLongPeriod() == null) {
            throw new IllegalArgumentException(
                "shortPeriod and longPeriod are required for MA_CROSSOVER alerts");
        }
        if (request.getShortPeriod() < 1 || request.getLongPeriod() < 1) {
            throw new IllegalArgumentException(
                "shortPeriod and longPeriod must be >= 1");
        }
        if (request.getShortPeriod() >= request.getLongPeriod()) {
            throw new IllegalArgumentException(
                "shortPeriod (" + request.getShortPeriod()
                + ") must be strictly less than longPeriod ("
                + request.getLongPeriod() + ")");
        }
        if (request.getMaType() == null || request.getMaType().isBlank()) {
            throw new IllegalArgumentException(
                "maType is required for MA_CROSSOVER alerts. Valid values: SMA, EMA");
        }
        String normalizedMaType = request.getMaType().toUpperCase();
        if (!"SMA".equals(normalizedMaType) && !"EMA".equals(normalizedMaType)) {
            throw new IllegalArgumentException(
                "Invalid maType: '" + request.getMaType()
                + "'. Valid values: SMA, EMA");
        }
        // Normalise le maType pour cohérence en base
        request.setMaType(normalizedMaType);
    }
}
