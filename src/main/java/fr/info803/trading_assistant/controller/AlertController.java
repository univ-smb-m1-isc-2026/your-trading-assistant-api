package fr.info803.trading_assistant.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.info803.trading_assistant.dto.AlertResponse;
import fr.info803.trading_assistant.dto.CreateAlertRequest;
import fr.info803.trading_assistant.dto.TriggeredAlertResponse;
import fr.info803.trading_assistant.dto.UpdateAlertRequest;
import fr.info803.trading_assistant.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/*
    Contrôleur REST pour le système d'alertes de trading.

    Pourquoi un contrôleur séparé d'AssetController et FavoriteController ?
    - Single Responsibility Principle : les alertes sont une resource indépendante
      avec leur propre cycle de vie (CRUD + évaluation + historique).
    - Les alertes sont identifiées par un ID numérique (pas un symbol), ce qui
      diffère fondamentalement du routing des assets et favoris.
    - Le préfixe /alerts évite toute ambiguïté de routing avec les endpoints existants
      (/assets, /assets/favorites, /assets/{symbol}/candles, etc.).

    Routes :

    GET    /alerts            → liste les alertes configurées par l'utilisateur
    POST   /alerts            → crée une nouvelle alerte
    PUT    /alerts/{id}       → modifie une alerte existante (mise à jour partielle)
    DELETE /alerts/{id}       → supprime une alerte et son historique de déclenchements
    GET    /alerts/triggered  → liste l'historique des alertes déclenchées

    Injection de l'utilisateur authentifié :
    - Spring MVC injecte automatiquement Authentication comme paramètre de méthode.
    - authentication.getName() retourne l'email (subject du JWT).
    - Même approche que FavoriteController pour la testabilité sans SecurityContext.

    Codes de retour :
    - GET    → 200 OK avec la liste (vide si aucune alerte/aucun déclenchement)
    - POST   → 201 Created avec l'alerte créée (convention REST : nouvelle resource)
    - PUT    → 200 OK avec l'alerte modifiée
    - DELETE → 204 No Content (action réussie, pas de corps de réponse)

    Erreurs :
    - 404 : alerte inexistante ou n'appartenant pas à l'utilisateur
            (AlertNotFoundException → GlobalExceptionHandler)
    - 404 : symbol inconnu lors de la création
            (AssetNotFoundException → GlobalExceptionHandler)
    - 400 : type ou direction d'alerte invalide
            (IllegalArgumentException → code 400 par défaut de Spring)

    Validation :
    - @Valid sur le @RequestBody de POST déclenche les validations Jakarta
      (@NotBlank, @NotNull) définies dans CreateAlertRequest. Spring renvoie
      automatiquement un 400 avec les messages d'erreur si la validation échoue.
    - PUT utilise UpdateAlertRequest qui n'a PAS de @NotNull/@NotBlank car
      tous les champs sont optionnels (mise à jour partielle).
*/
@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    /*
        GET /alerts
        Liste toutes les alertes configurées par l'utilisateur connecté.
        Inclut les alertes actives ET inactives pour permettre au client
        de réactiver des alertes one-shot ou de consulter l'historique.

        Réponse 200 OK :
          [
            {
              "id": 1,
              "symbol": "BTC",
              "type": "PRICE_THRESHOLD",
              "direction": "ABOVE",
              "thresholdValue": 100000,
              "recurring": true,
              "active": true,
              "createdAt": "2026-02-28T10:30:00"
            }
          ]

        Réponse 200 OK si aucune alerte :
          []
    */
    @GetMapping
    public ResponseEntity<List<AlertResponse>> getAlerts(Authentication authentication) {
        return ResponseEntity.ok(alertService.getAlerts(authentication.getName()));
    }

    /*
        GET /alerts/triggered
        Liste l'historique des alertes déclenchées pour l'utilisateur connecté.
        Trié par date de déclenchement décroissant (le plus récent en premier).

        Réponse 200 OK :
          [
            {
              "id": 1,
              "alertId": 3,
              "symbol": "BTC",
              "type": "PRICE_THRESHOLD",
              "direction": "ABOVE",
              "thresholdValue": 100000,
              "triggeredValue": 101500.50,
              "candleDate": "2026-02-27",
              "triggeredAt": "2026-02-28T00:05:30"
            }
          ]

        Réponse 200 OK si aucun déclenchement :
          []
    */
    @GetMapping("/triggered")
    public ResponseEntity<List<TriggeredAlertResponse>> getTriggeredAlerts(Authentication authentication) {
        return ResponseEntity.ok(alertService.getTriggeredAlerts(authentication.getName()));
    }

    /*
        POST /alerts
        Crée une nouvelle alerte pour l'utilisateur connecté.

        Corps de la requête :
          {
            "symbol": "BTC",
            "type": "PRICE_THRESHOLD",
            "direction": "ABOVE",
            "thresholdValue": 100000,
            "recurring": true
          }

        Réponse 201 Created : alerte créée (active par défaut).
        Réponse 400 Bad Request : champ manquant ou type/direction invalide.
        Réponse 404 Not Found : symbol inconnu.

        Pourquoi 201 et pas 200 ?
        - La convention REST recommande 201 Created pour les endpoints qui
          créent une nouvelle resource. Le corps de la réponse contient la
          resource créée avec son ID généré par la base de données.
    */
    @PostMapping
    public ResponseEntity<AlertResponse> createAlert(
            @Valid @RequestBody CreateAlertRequest request,
            Authentication authentication) {
        AlertResponse response = alertService.createAlert(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /*
        PUT /alerts/{id}
        Modifie une alerte existante de l'utilisateur connecté.
        Seuls les champs présents dans le corps sont modifiés (mise à jour partielle).

        Corps de la requête (tous les champs sont optionnels) :
          { "thresholdValue": 105000 }              → ne modifie que le seuil
          { "active": true }                         → réactive une alerte one-shot
          { "type": "VOLUME_THRESHOLD", "direction": "BELOW" } → change type et direction

        Réponse 200 OK : alerte modifiée.
        Réponse 404 Not Found : alerte inexistante ou n'appartenant pas à l'utilisateur.
        Réponse 400 Bad Request : type ou direction invalide.
    */
    @PutMapping("/{id}")
    public ResponseEntity<AlertResponse> updateAlert(
            @PathVariable Long id,
            @RequestBody UpdateAlertRequest request,
            Authentication authentication) {
        AlertResponse response = alertService.updateAlert(authentication.getName(), id, request);
        return ResponseEntity.ok(response);
    }

    /*
        DELETE /alerts/{id}
        Supprime une alerte et tout son historique de déclenchements.
        L'opération est atomique (@Transactional dans le service).

        Réponse 204 No Content : alerte et historique supprimés.
        Réponse 404 Not Found : alerte inexistante ou n'appartenant pas à l'utilisateur.
    */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlert(
            @PathVariable Long id,
            Authentication authentication) {
        alertService.deleteAlert(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
