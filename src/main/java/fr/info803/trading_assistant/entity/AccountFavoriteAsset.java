package fr.info803.trading_assistant.entity;

import java.time.LocalDateTime;

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
    Représente un asset mis en favori par un utilisateur.

    Table de jointure explicite entre Account et Asset.

    Pourquoi une entité dédiée plutôt qu'un @ManyToMany ?
    - Un @ManyToMany avec @JoinTable crée la table implicitement, sans
      possibilité d'y stocker des métadonnées (comme favoritedAt).
    - Une entité dédiée est extensible : on peut ajouter un champ "note",
      une catégorie, ou un ordre de tri sans refactoring.
    - C'est cohérent avec le style du projet (toutes les entités ont un ID séquence).

    Contrainte UNIQUE (account_id, asset_id) :
    - Empêche les doublons au niveau base de données.
    - C'est un filet de sécurité complémentaire au check applicatif dans
      FavoriteService : même si deux requêtes simultanées arrivaient,
      la DB rejette la seconde insertion.

    favoritedAt :
    - Enregistre quand l'asset a été mis en favori.
    - Permet de trier par date d'ajout et d'auditer l'historique.
    - Initialisé à LocalDateTime.now() dans FavoriteService.addFavorite().
*/
@Entity
@Table(
    name = "account_favorite_asset",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_account_favorite_asset",
        columnNames = {"account_id", "asset_id"}
    )
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AccountFavoriteAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "account_favorite_asset_seq")
    @SequenceGenerator(name = "account_favorite_asset_seq", sequenceName = "account_favorite_asset_sequence", allocationSize = 1)
    private Long id;

    /*
        FetchType.LAZY : l'Account n'est pas chargé automatiquement.
        On charge l'account uniquement quand on y accède explicitement,
        ce qui évite un JOIN inutile dans les requêtes qui n'en ont pas besoin.
    */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /*
        FetchType.LAZY : même raison. L'Asset est chargé à la demande.
        Note : dans FavoriteService.getFavorites(), on accède au symbol via
        adv.getAsset().getSymbol() — Spring JPA gère le lazy-load dans le
        contexte transactionnel implicite de la requête.
    */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(nullable = false)
    private LocalDateTime favoritedAt;
}
