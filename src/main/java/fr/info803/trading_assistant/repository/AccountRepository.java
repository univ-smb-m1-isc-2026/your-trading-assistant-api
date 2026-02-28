package fr.info803.trading_assistant.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import fr.info803.trading_assistant.entity.Account;

/*
    Repository Spring Data JPA pour l'entité Account.

    JpaRepository fournit automatiquement les opérations CRUD (save, findById, delete, etc.)
    sans écrire une seule ligne de SQL.

    On ajoute deux méthodes dérivées :
    - findByEmail : utilisée par AccountService pour charger un compte à la connexion
    - existsByEmail : utilisée pour vérifier qu'un email n'est pas déjà pris à l'inscription
*/
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);
}
