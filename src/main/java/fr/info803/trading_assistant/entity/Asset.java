package fr.info803.trading_assistant.entity;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
    Entité JPA représentant un actif financier (crypto, action, indice...).

    Relation avec AssetSource :
      - @ManyToOne : un Asset appartient à UNE seule AssetSource.
      - Plusieurs assets peuvent partager la même source (ex: BTC et ETH viennent tous deux
        de Hyperliquid).
      - FetchType.LAZY : l'AssetSource n'est chargée depuis la DB que si on y accède
        explicitement. Évite les N+1 queries en ne chargeant pas systématiquement la source
        lors d'une liste d'assets.

    Exemple de données :
      symbol = "BTC", source = AssetSource(name = "hyperliquid")
*/
@Entity
@Table(name = "asset")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "asset_seq")
    @SequenceGenerator(name = "asset_seq", sequenceName = "asset_sequence", allocationSize = 1)
    private Long id;

    // Symbole de marché de l'actif (ex: "BTC", "ETH", "AAPL").
    // Unique car deux assets ne peuvent pas avoir le même symbole dans le système.
    @Column(unique = true, nullable = false)
    private String symbol;

    // Source de données pour cet asset.
    // FetchType.LAZY : charge l'AssetSource uniquement si accédée (optimisation performances).
    // nullable = false : un Asset doit toujours avoir une source connue.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private AssetSource source;
}
