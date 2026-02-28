package fr.info803.trading_assistant.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetDailyValue;

/*
    Repository Spring Data JPA pour AssetDailyValue.

    Méthodes custom :

    1. findByAssetAndDate(Asset asset, LocalDate date)
       - Clé de l'upsert : avant d'insérer une valeur journalière, on vérifie si
         une entrée existe déjà pour ce couple (asset, date).
       - Si présente → UPDATE les champs OHLCV (évite les doublons malgré les re-runs).
       - Si absente → INSERT une nouvelle ligne.
       - Retourne Optional<AssetDailyValue> pour un traitement explicite des deux cas.
       - SQL généré :
           SELECT * FROM asset_daily_value WHERE asset_id = ? AND date = ?
       - La contrainte d'unicité en DB (uk_asset_daily_value_asset_date) sert de
         filet de sécurité côté base si deux threads tentent d'insérer simultanément.

    2. findLatestForAllAssets()
       - Récupère la dernière bougie pour chaque asset (scalable pour des centaines d'assets).
       - Utilise une sous-requête corrélée en JPQL pour éviter les N+1 queries.
       - Requête JPQL :
           SELECT adv FROM AssetDailyValue adv
           JOIN FETCH adv.asset a
           WHERE adv.date = (SELECT MAX(adv2.date) FROM AssetDailyValue adv2 WHERE adv2.asset = adv.asset)
       - Complexité : 2 queries SQL au total, peu importe le nombre d'assets.
       - Raison du FETCH : évite une N+1 sur le chargement des assets (LAZY par défaut).
       - Les assets sans prix ne sont pas retournés — le service les ajoute avec null.

    3. findByAssetAndDateGreaterThanEqualOrderByDateAsc(Asset asset, LocalDate fromDate)
       - Récupère toutes les bougies d'un asset depuis une date donnée, triées par date ASC.
       - Utilisée pour GET /assets/{symbol}/candles.
       - SQL généré :
           SELECT * FROM asset_daily_value
           WHERE asset_id = ? AND date >= ?
           ORDER BY date ASC
*/
public interface AssetDailyValueRepository extends JpaRepository<AssetDailyValue, Long> {

    Optional<AssetDailyValue> findByAssetAndDate(Asset asset, LocalDate date);

    @Query(
        "SELECT adv FROM AssetDailyValue adv " +
        "JOIN FETCH adv.asset a " +
        "WHERE adv.date = (SELECT MAX(adv2.date) FROM AssetDailyValue adv2 WHERE adv2.asset = adv.asset)"
    )
    List<AssetDailyValue> findLatestForAllAssets();

    List<AssetDailyValue> findByAssetAndDateGreaterThanEqualOrderByDateAsc(Asset asset, LocalDate fromDate);
}

