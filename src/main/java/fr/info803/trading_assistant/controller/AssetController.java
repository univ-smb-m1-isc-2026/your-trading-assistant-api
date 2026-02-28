package fr.info803.trading_assistant.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fr.info803.trading_assistant.dto.AssetSummaryResponse;
import fr.info803.trading_assistant.dto.CandleResponse;
import fr.info803.trading_assistant.dto.MovingAverageSeriesResponse;
import fr.info803.trading_assistant.service.AssetService;
import fr.info803.trading_assistant.service.MovingAverageService;
import lombok.RequiredArgsConstructor;

/*
    Contrôleur REST pour les endpoints asset.

    Tous les endpoints sont **protégés par JWT** — le middleware d'authentification
    rejette les requêtes sans Bearer token valide (sauf pour /auth/** et /dev/**).

    Routes :

    1. GET /assets
       - Retourne tous les assets avec leur dernier prix.
       - Réponse :
         [
           { "symbol": "BTC", "lastPrice": "96000.0", "lastDate": "2025-01-15" },
           { "symbol": "ETH", "lastPrice": null,      "lastDate": null }
         ]

    2. GET /assets/{symbol}/candles
       - Retourne toutes les bougies d'un asset sur les 12 derniers mois.
       - Réponse :
         [
           { "date": "2024-01-15", "open": "42000", "high": "43000",
             "low": "41500", "close": "42800", "volume": "1234.5" },
           ...
         ]
       - 404 si symbol inconnu :
         { "error": "Asset not found", "symbol": "UNKNOWN", "timestamp": "2025-01-15T12:34:56" }
    3. GET /assets/{symbol}/moving-averages?type=SMA&periods=20,50
       - Retourne les moyennes mobiles calculées à la volée.
       - Paramètres :
         - type    : "SMA" ou "EMA" (obligatoire)
         - periods : liste d'entiers séparés par virgule (obligatoire)
       - Réponse :
         [
           {
             "type": "SMA",
             "period": 20,
             "values": [
               { "date": "2026-02-01", "value": 95123.45 },
               ...
             ]
           }
         ]
       - 400 si type ou periods invalide.
       - 404 si symbol inconnu.
*/
@RestController
@RequestMapping("/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;
    private final MovingAverageService movingAverageService;

    /*
        GET /assets
        Récupère tous les assets avec leur dernier prix enregistré en base.

        JWT requis : la sécurité Spring rejette les requêtes sans token valide.

        Réponse 200 OK :
          Content-Type: application/json
          [
            { "symbol": "BTC", "lastPrice": "96000.0", "lastDate": "2025-01-15" },
            { "symbol": "ETH", "lastPrice": "3200.5", "lastDate": "2025-01-15" },
            ...
          ]

        Cas des assets sans prix : inclus dans la liste avec lastPrice=null, lastDate=null.
    */
    @GetMapping
    public ResponseEntity<List<AssetSummaryResponse>> getAssets() {
        return ResponseEntity.ok(assetService.getAssetSummaries());
    }

    /*
        GET /assets/{symbol}/candles
        Récupère toutes les bougies d'un asset sur les 12 derniers mois.

        Paramètres :
          - symbol : symbole de l'asset (ex: "BTC", "ETH", "MANTA")

        JWT requis : la sécurité Spring rejette les requêtes sans token valide.

        Réponse 200 OK :
          Content-Type: application/json
          [
            { "date": "2024-01-15", "open": "42000", "high": "43000",
              "low": "41500", "close": "42800", "volume": "1234.5" },
            ...
          ]

        Réponse 404 Not Found (si symbol inconnu) :
          Content-Type: application/json
          { "error": "Asset not found", "symbol": "UNKNOWN", "timestamp": "2025-01-15T12:34:56" }
    */
    @GetMapping("/{symbol}/candles")
    public ResponseEntity<List<CandleResponse>> getCandles(@PathVariable String symbol) {
        return ResponseEntity.ok(assetService.getCandles(symbol));
    }

    /*
        GET /assets/{symbol}/moving-averages?type=SMA&periods=20,50
        Calcule les moyennes mobiles à la volée à partir des bougies existantes.

        Paramètres :
          - symbol  : symbole de l'asset (ex: "BTC", "ETH")
          - type    : type de MA, "SMA" ou "EMA" (obligatoire, insensible à la casse)
          - periods : liste de périodes séparées par virgule, ex: "20,50" (obligatoire)

        JWT requis : la sécurité Spring rejette les requêtes sans token valide.

        Réponse 200 OK :
          Content-Type: application/json
          [
            {
              "type": "SMA",
              "period": 20,
              "values": [
                { "date": "2026-02-01", "value": 95123.45 },
                ...
              ]
            }
          ]

        Réponse 400 Bad Request (si type ou periods invalide) :
          { "error": "Invalid moving average request", "message": "...", "timestamp": "..." }

        Réponse 404 Not Found (si symbol inconnu) :
          { "error": "Asset not found", "symbol": "UNKNOWN", "timestamp": "..." }
    */
    @GetMapping("/{symbol}/moving-averages")
    public ResponseEntity<List<MovingAverageSeriesResponse>> getMovingAverages(
            @PathVariable String symbol,
            @RequestParam String type,
            @RequestParam List<Integer> periods) {
        return ResponseEntity.ok(movingAverageService.getMovingAverages(symbol, type, periods));
    }
}
