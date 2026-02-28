package fr.info803.trading_assistant.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.info803.trading_assistant.dto.AssetSummaryResponse;
import fr.info803.trading_assistant.service.FavoriteService;
import lombok.RequiredArgsConstructor;

/*
    Contrôleur REST pour la gestion des assets favoris d'un utilisateur.

    Pourquoi un contrôleur séparé d'AssetController ?
    - Single Responsibility Principle : AssetController gère les assets (données
      de marché), FavoriteController gère la relation utilisateur ↔ asset.
    - Les favoris sont une resource de l'utilisateur authentifié, pas une resource
      d'asset au sens global.
    - Cela facilite l'ajout future de logique spécifique aux favoris (pagination,
      filtres, etc.) sans modifier AssetController.

    Routes — toutes sous /assets pour cohabiter avec AssetController :

    GET    /assets/favorites          → liste les favoris de l'utilisateur connecté
    POST   /assets/{symbol}/favorite  → ajoute un asset aux favoris
    DELETE /assets/{symbol}/favorite  → retire un asset des favoris

    Résolution des ambiguïtés de routing Spring MVC :
    - GET /assets/favorites : Spring MVC préfère les segments littéraux ("favorites")
      aux variables de chemin ({symbol}). Donc cette route ne conflicte PAS avec
      GET /assets/{symbol}/candles défini dans AssetController.
    - GET /assets/favorites/candles : appellerait getCandles("favorites") dans
      AssetController — ce cas n'existe pas en pratique (pas d'asset nommé "favorites").

    Injection de l'utilisateur authentifié :
    - Spring MVC injecte automatiquement Authentication comme paramètre de méthode.
    - authentication.getName() retourne l'email (le subject du JWT, défini dans
      Account.getUsername() qui délègue à l'email).
    - C'est plus propre que SecurityContextHolder.getContext().getAuthentication()
      car c'est testable sans setup de SecurityContext.

    Codes de retour :
    - GET  → 200 OK avec la liste (vide si aucun favori)
    - POST → 204 No Content (action réussie, pas de corps de réponse)
    - DELETE → 204 No Content (action réussie, pas de corps de réponse)

    Erreurs :
    - 404 : symbol inconnu (AssetNotFoundException → GlobalExceptionHandler)
    - 409 : asset déjà en favori (FavoriteAlreadyExistsException → GlobalExceptionHandler)
    - 404 : asset pas en favori lors d'un DELETE (FavoriteNotFoundException → GlobalExceptionHandler)
*/
@RestController
@RequestMapping("/assets")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /*
        GET /assets/favorites
        Liste tous les assets en favori de l'utilisateur connecté.

        Réponse 200 OK (même format que GET /assets) :
          [
            { "symbol": "BTC", "lastPrice": "96000.0", "lastDate": "2025-01-15" },
            { "symbol": "ETH", "lastPrice": "3200.5",  "lastDate": "2025-01-15" }
          ]

        Réponse 200 OK si aucun favori :
          []
    */
    @GetMapping("/favorites")
    public ResponseEntity<List<AssetSummaryResponse>> getFavorites(Authentication authentication) {
        return ResponseEntity.ok(favoriteService.getFavorites(authentication.getName()));
    }

    /*
        POST /assets/{symbol}/favorite
        Ajoute l'asset identifié par {symbol} aux favoris de l'utilisateur connecté.

        Réponse 204 No Content : favori ajouté.
        Réponse 404 Not Found : symbol inconnu.
        Réponse 409 Conflict : asset déjà en favori.
    */
    @PostMapping("/{symbol}/favorite")
    public ResponseEntity<Void> addFavorite(@PathVariable String symbol, Authentication authentication) {
        favoriteService.addFavorite(authentication.getName(), symbol);
        return ResponseEntity.noContent().build();
    }

    /*
        DELETE /assets/{symbol}/favorite
        Retire l'asset identifié par {symbol} des favoris de l'utilisateur connecté.

        Réponse 204 No Content : favori retiré.
        Réponse 404 Not Found : symbol inconnu OU asset pas en favori.
    */
    @DeleteMapping("/{symbol}/favorite")
    public ResponseEntity<Void> removeFavorite(@PathVariable String symbol, Authentication authentication) {
        favoriteService.removeFavorite(authentication.getName(), symbol);
        return ResponseEntity.noContent().build();
    }
}
