package fr.info803.trading_assistant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.Alert;

/*
    Repository Spring Data JPA pour la table alert.

    Méthodes custom :

    1. findByAccount(Account account)
       → SELECT * FROM alert WHERE account_id = ?
       Récupère toutes les alertes configurées par un utilisateur.
       Utilisé par GET /alerts.

    2. findByIdAndAccount(Long id, Account account)
       → SELECT * FROM alert WHERE id = ? AND account_id = ?
       Récupère une alerte spécifique appartenant à un utilisateur.
       La double condition (id + account) empêche un utilisateur d'accéder
       aux alertes d'un autre — sécurité au niveau query.
       Utilisé par PUT /alerts/{id} et DELETE /alerts/{id}.

    3. findByActiveTrue()
       → SELECT * FROM alert WHERE active = true
       Récupère toutes les alertes actives de tous les utilisateurs.
       Utilisé par le scheduler nightly pour l'évaluation des alertes.
*/
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByAccount(Account account);

    Optional<Alert> findByIdAndAccount(Long id, Account account);

    List<Alert> findByActiveTrue();
}
