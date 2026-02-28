package fr.info803.trading_assistant.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import fr.info803.trading_assistant.entity.AssetSource;

/*
    Repository Spring Data JPA pour AssetSource.

    Spring Data génère automatiquement toutes les implémentations CRUD à partir
    de l'interface — aucun code SQL à écrire.

    Méthode custom "findByName" :
      - Utilisée par AssetDataSyncService pour retrouver une source par son nom logique
        (ex: "hyperliquid") et vérifier qu'un provider correspondant existe avant de fetch.
      - Retourne Optional<AssetSource> : force l'appelant à gérer le cas "source inconnue"
        sans risque de NullPointerException.
      - Spring Data dérive la requête SQL depuis le nom de la méthode :
          SELECT * FROM asset_source WHERE name = ?
*/
public interface AssetSourceRepository extends JpaRepository<AssetSource, Long> {

    Optional<AssetSource> findByName(String name);
}
