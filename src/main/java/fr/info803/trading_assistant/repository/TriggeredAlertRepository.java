package fr.info803.trading_assistant.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.Alert;
import fr.info803.trading_assistant.entity.TriggeredAlert;

/*
    Repository Spring Data JPA pour la table triggered_alert.

    Méthodes custom :

    1. findByAlertAccountOrderByTriggeredAtDesc(Account account)
       → Requête dérivée traversant la relation TriggeredAlert → Alert → Account.
       Spring Data JPA interprète le chemin "Alert.Account" et génère le JOIN.
       SQL généré (simplifié) :
         SELECT ta.* FROM triggered_alert ta
         JOIN alert a ON ta.alert_id = a.id
         WHERE a.account_id = ?
         ORDER BY ta.triggered_at DESC
       Tri par date de déclenchement décroissant (le plus récent en premier).
       Utilisé par GET /alerts/triggered.

    2. existsByAlertAndCandleDate(Alert alert, LocalDate candleDate)
       → SELECT COUNT(*) > 0 FROM triggered_alert WHERE alert_id = ? AND candle_date = ?
       Vérification anti-doublon : empêche de déclencher la même alerte deux fois
       pour la même journée (re-run du scheduler, relance manuelle...).
       Complémentaire de la contrainte UNIQUE en base (filet de sécurité DB).

    3. deleteByAlert(Alert alert)
       → Supprime tous les déclenchements associés à une alerte.
       Utilisé lors de la suppression d'une alerte (cascade applicative).
       Requiert @Transactional au niveau service.
*/
public interface TriggeredAlertRepository extends JpaRepository<TriggeredAlert, Long> {

    List<TriggeredAlert> findByAlertAccountOrderByTriggeredAtDesc(Account account);

    boolean existsByAlertAndCandleDate(Alert alert, LocalDate candleDate);

    void deleteByAlert(Alert alert);
}
