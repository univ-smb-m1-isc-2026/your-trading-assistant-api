package fr.info803.trading_assistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
    Entité JPA représentant une source de données de marché (ex: Hyperliquid, Alpha Vantage).

    Rôle dans l'architecture :
      - Chaque AssetSource correspond à un fournisseur d'API externe.
      - Son champ "name" est utilisé par le Strategy Pattern pour sélectionner
        le bon AssetDataProvider au moment du fetch nocturne.
      - Son champ "url" est l'URL de base de l'API, configurable directement
        depuis la base de données sans redéploiement.

    Exemple de données :
      name = "hyperliquid", url = "https://api.hyperliquid.xyz/info"
*/
@Entity
@Table(name = "asset_source")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetSource {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "asset_source_seq")
    @SequenceGenerator(name = "asset_source_seq", sequenceName = "asset_source_sequence", allocationSize = 1)
    private Long id;

    // Identifiant logique de la source — doit correspondre à AssetDataProvider.getSourceName()
    // Unique car un seul provider par source est géré à la fois.
    @Column(unique = true, nullable = false)
    private String name;

    // URL de base de l'API externe.
    // Passée au provider lors du fetch pour éviter de hardcoder les URLs dans le code.
    @Column(nullable = false)
    private String url;
}
