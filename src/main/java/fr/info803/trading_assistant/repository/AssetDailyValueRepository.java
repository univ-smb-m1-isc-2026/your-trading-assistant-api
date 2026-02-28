package fr.info803.trading_assistant.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetDailyValue;

/*
    Repository Spring Data JPA pour AssetDailyValue.

    Méthode custom :

    findByAssetAndDate(Asset asset, LocalDate date)
      - Clé de l'upsert : avant d'insérer une valeur journalière, on vérifie si
        une entrée existe déjà pour ce couple (asset, date).
      - Si présente → UPDATE les champs OHLCV (évite les doublons malgré les re-runs).
      - Si absente → INSERT une nouvelle ligne.
      - Retourne Optional<AssetDailyValue> pour un traitement explicite des deux cas.
      - SQL généré :
          SELECT * FROM asset_daily_value WHERE asset_id = ? AND date = ?
      - La contrainte d'unicité en DB (uk_asset_daily_value_asset_date) sert de
        filet de sécurité côté base si deux threads tentent d'insérer simultanément.
*/
public interface AssetDailyValueRepository extends JpaRepository<AssetDailyValue, Long> {

    Optional<AssetDailyValue> findByAssetAndDate(Asset asset, LocalDate date);
}
