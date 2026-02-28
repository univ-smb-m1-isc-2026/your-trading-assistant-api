package fr.info803.trading_assistant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.AccountFavoriteAsset;
import fr.info803.trading_assistant.entity.Asset;

/*
    Repository JPA pour la table account_favorite_asset.

    Spring Data JPA génère automatiquement les implémentations SQL
    à partir des noms de méthodes (dérivation de requête) :

    findByAccount(account)
      → SELECT * FROM account_favorite_asset WHERE account_id = ?
      Utilisé pour lister tous les favoris d'un utilisateur.
      Le tri alphabétique est délégué au service (cohérence avec AssetService).

    findByAccountAndAsset(account, asset)
      → SELECT * FROM account_favorite_asset WHERE account_id = ? AND asset_id = ?
      Utilisé pour vérifier si un asset est déjà en favori avant l'ajout.

    existsByAccountAndAsset(account, asset)
      → SELECT COUNT(*) > 0 FROM account_favorite_asset WHERE account_id = ? AND asset_id = ?
      Vérification boolean — plus efficace que findBy...isPresent() car la DB
      peut s'arrêter dès qu'elle trouve une ligne (pas besoin de tout charger).

    deleteByAccountAndAsset(account, asset)
      → Spring Data JPA fait d'abord un SELECT pour charger l'entité,
        puis appelle EntityManager.remove(). Requiert un contexte transactionnel.
        Le @Transactional est positionné au niveau service (FavoriteService.removeFavorite).
*/
public interface AccountFavoriteAssetRepository extends JpaRepository<AccountFavoriteAsset, Long> {

    List<AccountFavoriteAsset> findByAccount(Account account);

    Optional<AccountFavoriteAsset> findByAccountAndAsset(Account account, Asset asset);

    boolean existsByAccountAndAsset(Account account, Asset asset);

    void deleteByAccountAndAsset(Account account, Asset asset);
}
