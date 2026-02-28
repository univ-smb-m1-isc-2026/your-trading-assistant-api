package fr.info803.trading_assistant.service;

import java.time.LocalDate;
import java.util.List;

import fr.info803.trading_assistant.dto.DailyValueDto;

/*
    Interface du Strategy Pattern pour les fournisseurs de données de marché.

    Pourquoi le Strategy Pattern ?
      - Vous avez prévu au moins 2 sources (Hyperliquid + une autre pour les stocks).
      - Sans ce pattern, AssetDataSyncService aurait un if/else ou un switch hardcodé
        pour chaque source : difficile à maintenir et à étendre.
      - Avec ce pattern : ajouter une 3ème source = créer une nouvelle classe qui
        implémente cette interface. Aucune modification du SyncService nécessaire.
      - C'est le principe "Open/Closed" (SOLID) : ouvert à l'extension, fermé à la modification.

    Contrat imposé à chaque implémentation :

    1. getSourceName()
       - Retourne le nom logique de la source, qui DOIT correspondre exactement
         à AssetSource.name en base de données.
       - Permet au SyncService de faire la correspondance source DB ↔ provider code.
       - Exemple : "hyperliquid", "alphavantage"

    2. fetchDailyValues(String symbol, LocalDate date, String apiUrl)
       - Récupère les données OHLCV d'un asset pour une date donnée.
       - symbol  : le ticker de l'asset (ex: "BTC", "ETH")
       - date    : la date cible (en général J-1 car le scheduler tourne à minuit)
       - apiUrl  : l'URL de base de l'API, lue depuis AssetSource.url en DB.
                   Cela rend l'URL configurable sans redéploiement.
       - Retourne List<DailyValueDto> : en général une seule entrée pour une date donnée,
         mais List permet de gérer des cas où l'API retourne plusieurs bougies.
       - En cas d'erreur réseau ou de parsing, le provider doit retourner une liste vide
         (jamais lever d'exception non gérée) pour permettre au SyncService de continuer
         avec les autres assets.
*/
public interface AssetDataProvider {

    String getSourceName();

    List<DailyValueDto> fetchDailyValues(String symbol, LocalDate date, String apiUrl);
}
