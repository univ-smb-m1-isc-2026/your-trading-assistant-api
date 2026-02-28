package fr.info803.trading_assistant.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
    Entité JPA représentant les données OHLCV (Open, High, Low, Close, Volume)
    journalières d'un actif financier.

    Modèle de données :
      - Chaque ligne = une bougie (candle) journalière pour un Asset à une date donnée.
      - La contrainte d'unicité (asset_id, date) garantit qu'il n'y a jamais deux entrées
        pour le même asset le même jour — même si le scheduler tourne plusieurs fois.
      - Cela permet d'implémenter une logique d'upsert simple :
          si (asset, date) existe → UPDATE, sinon → INSERT.

    Pourquoi BigDecimal pour les prix ?
      - BigDecimal offre une précision arithmétique exacte, indispensable en finance.
      - Double et Float sont des types à virgule flottante binaire : ils ne peuvent pas
        représenter exactement toutes les fractions décimales (ex: 0.1 + 0.2 ≠ 0.3).
      - precision = 30 : supporte des prix jusqu'à 10^20 (largement suffisant).
      - scale = 10 : 10 décimales pour les crypto à faible valeur unitaire (ex: SHIB).

    Exemple de données :
      asset = Asset(symbol = "BTC"), date = 2025-01-15,
      open = 95000.00, high = 96500.00, low = 94200.00, close = 96000.00, volume = 28345.12
*/
@Entity
@Table(
    name = "asset_daily_value",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_asset_daily_value_asset_date",
        columnNames = {"asset_id", "date"}
    )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetDailyValue {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "asset_daily_value_seq")
    @SequenceGenerator(
        name = "asset_daily_value_seq",
        sequenceName = "asset_daily_value_sequence",
        allocationSize = 1
    )
    private Long id;

    // Asset auquel appartient cette valeur journalière.
    // FetchType.LAZY : l'Asset n'est chargé que si accédé explicitement.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    // Date de la bougie journalière (ex: 2025-01-15).
    // Combiné avec asset_id pour former la contrainte d'unicité.
    @Column(nullable = false)
    private LocalDate date;

    // Prix d'ouverture : premier prix de la journée de trading.
    @Column(nullable = false, precision = 30, scale = 10)
    private BigDecimal open;

    // Prix le plus haut atteint durant la journée.
    @Column(nullable = false, precision = 30, scale = 10)
    private BigDecimal high;

    // Prix le plus bas atteint durant la journée.
    @Column(nullable = false, precision = 30, scale = 10)
    private BigDecimal low;

    // Prix de clôture : dernier prix de la journée de trading.
    @Column(nullable = false, precision = 30, scale = 10)
    private BigDecimal close;

    // Volume total échangé sur la journée (en unités de l'asset, pas en USD).
    @Column(nullable = false, precision = 30, scale = 10)
    private BigDecimal volume;
}
