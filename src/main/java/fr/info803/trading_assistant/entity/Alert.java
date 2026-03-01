package fr.info803.trading_assistant.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
    Représente une alerte configurée par un utilisateur sur un actif.

    Table de jointure entre Account et Asset, enrichie de métadonnées
    (type, direction, seuil, récurrence, état actif).

    Pourquoi pas de contrainte UNIQUE (account, asset, type, direction) ?
    - Un utilisateur peut vouloir plusieurs alertes sur le même asset :
      "BTC > 100k" ET "BTC > 110k", ou "BTC > 100k" ET "BTC < 90k".
    - Chaque alerte est identifiée individuellement par son ID.

    Champs principaux :

    type (AlertType) :
      - Détermine quel AlertEvaluator prend en charge cette alerte.
      - Stocké comme String en base (EnumType.STRING) pour la lisibilité.

    direction (AlertDirection) :
      - ABOVE : la valeur observée doit être >= threshold.
      - BELOW : la valeur observée doit être <= threshold.
      - Pour PRICE_THRESHOLD + ABOVE, on compare candle.high (le plus haut de la journée).
      - Pour PRICE_THRESHOLD + BELOW, on compare candle.low (le plus bas de la journée).

    thresholdValue (BigDecimal) :
      - Le seuil configuré par l'utilisateur (ex: 100000.00 pour BTC > 100k$).
      - Même précision que les prix (30,10) pour cohérence.

    recurring (boolean) :
      - true  = l'alerte reste active après déclenchement (se déclenche à chaque
        jour où la condition est remplie).
      - false = l'alerte se désactive automatiquement après le premier déclenchement
        (one-shot). L'utilisateur peut la réactiver manuellement.

    active (boolean) :
      - true  = l'alerte est évaluée lors du sync nightly.
      - false = l'alerte est ignorée (désactivée manuellement ou par one-shot trigger).

    createdAt (LocalDateTime) :
      - Horodatage de création, initialisé dans AlertService.createAlert().
*/
@Entity
@Table(name = "alert")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "alert_seq")
    @SequenceGenerator(name = "alert_seq", sequenceName = "alert_sequence", allocationSize = 1)
    private Long id;

    /*
        FetchType.LAZY : le compte n'est chargé que si accédé explicitement.
        Même pattern que AccountFavoriteAsset.
    */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /*
        FetchType.LAZY : l'asset n'est chargé que si accédé explicitement.
        Utilisé pour récupérer le symbole lors de la construction de la réponse API.
    */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    // Type de condition : PRICE_THRESHOLD, VOLUME_THRESHOLD, etc.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType type;

    // Direction de comparaison : ABOVE (>=) ou BELOW (<=).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertDirection direction;

    // Seuil configuré par l'utilisateur (prix ou volume selon le type).
    @Column(nullable = false, precision = 30, scale = 10)
    private BigDecimal thresholdValue;

    // true = reste active après déclenchement ; false = one-shot (désactivation auto).
    @Column(nullable = false)
    private boolean recurring;

    // true = évaluée lors du sync nightly ; false = ignorée.
    @Column(nullable = false)
    private boolean active;

    // Horodatage de création.
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
