package fr.info803.trading_assistant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetSource;

/*
    Repository Spring Data JPA pour Asset.

    Méthodes custom :

    1. findBySymbol(String symbol)
       - Permet de retrouver un asset par son symbole de marché (ex: "BTC").
       - Utile pour dédupliquer lors d'un futur import ou d'une API REST.
       - Retourne Optional<Asset> pour forcer la gestion du cas "asset inconnu".
       - SQL généré : SELECT * FROM asset WHERE symbol = ?

    2. findBySource(AssetSource source)
       - Retourne tous les assets liés à une source donnée.
       - Utilisée par AssetDataSyncService : pour chaque AssetSource en DB,
         on charge tous ses assets puis on itère pour fetch les prix.
       - SQL généré : SELECT * FROM asset WHERE source_id = ?
*/
public interface AssetRepository extends JpaRepository<Asset, Long> {

    Optional<Asset> findBySymbol(String symbol);

    List<Asset> findBySource(AssetSource source);
}
