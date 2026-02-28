package fr.info803.trading_assistant.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.info803.trading_assistant.dto.AssetSummaryResponse;
import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.AccountFavoriteAsset;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetDailyValue;
import fr.info803.trading_assistant.exception.AssetNotFoundException;
import fr.info803.trading_assistant.exception.FavoriteAlreadyExistsException;
import fr.info803.trading_assistant.exception.FavoriteNotFoundException;
import fr.info803.trading_assistant.repository.AccountFavoriteAssetRepository;
import fr.info803.trading_assistant.repository.AccountRepository;
import fr.info803.trading_assistant.repository.AssetDailyValueRepository;
import fr.info803.trading_assistant.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
    Service métier pour les endpoints favoris.

    Contrats :

    1. getFavorites(email)
       - Retourne les assets en favori de l'utilisateur avec leur dernier prix connu.
       - Stratégie : 2 queries SQL (même approche scalable qu'AssetService) :
         Query 1 : SELECT * FROM account_favorite_asset WHERE account_id = ?
         Query 2 : findLatestForAllAssets() — réutilise la query JPQL existante
       - Fusion en mémoire : Map<assetId, latestCandle> puis filtre sur les favoris.
       - Tri : alphabétique par symbol (cohérence avec GET /assets).
       - Note sur le choix de réutiliser findLatestForAllAssets() :
         On récupère les dernières bougies pour TOUS les assets, puis on filtre
         aux favoris. Si la base contient 1000 assets et que l'utilisateur en a
         5 en favori, c'est légèrement inefficace. L'optimisation serait une
         @Query ciblée sur les assets favoris. Pour l'échelle actuelle (< 100
         assets), cet overhead est négligeable.

    2. addFavorite(email, symbol)
       - Ajoute l'asset aux favoris de l'utilisateur.
       - Lève AssetNotFoundException si le symbole est inconnu.
       - Lève FavoriteAlreadyExistsException si déjà en favori.
       - Le check applicatif (existsByAccountAndAsset) est un first-line defense.
         La contrainte UNIQUE en base est le filet de sécurité ultime contre
         les race conditions.

    3. removeFavorite(email, symbol)
       - Retire l'asset des favoris de l'utilisateur.
       - Lève AssetNotFoundException si le symbole est inconnu.
       - Lève FavoriteNotFoundException si l'asset n'est pas en favori.
       - @Transactional requis : Spring Data JPA's deleteBy...() fait d'abord
         un SELECT puis un DELETE — l'ensemble doit être dans une même transaction.
*/
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final AccountRepository accountRepository;
    private final AssetRepository assetRepository;
    private final AccountFavoriteAssetRepository favoriteRepository;
    private final AssetDailyValueRepository assetDailyValueRepository;

    /*
        Charge l'utilisateur depuis la base.
        Utilisé en interne par chaque méthode publique du service.

        Le sujet JWT (authentication.getName()) est l'email (voir Account.getUsername()).
        On lève UsernameNotFoundException (Spring Security) plutôt qu'une exception
        custom car cet état ne devrait jamais arriver : le JWT est validé en amont
        par JwtAuthenticationFilter, donc le compte existe forcément en base.
    */
    private Account loadAccount(String email) {
        return accountRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("Account not found: " + email));
    }

    /*
        Récupère tous les assets en favori de l'utilisateur avec leur dernier prix.

        Flux :
          1. Charge le compte via email.
          2. Charge la liste des favoris (AccountFavoriteAsset → Asset lazy-loaded).
          3. Récupère les dernières bougies pour tous les assets.
          4. Construit une Map assetId → bougie pour fusion O(1).
          5. Pour chaque favori : cherche sa bougie dans la map.
          6. Trie par symbol ASC.
    */
    public List<AssetSummaryResponse> getFavorites(String email) {
        log.info("Fetching favorites for account={}", email);

        Account account = loadAccount(email);

        List<AccountFavoriteAsset> favorites = favoriteRepository.findByAccount(account);

        if (favorites.isEmpty()) {
            log.info("No favorites found for account={}", email);
            return List.of();
        }

        // Lookup O(1) : Map asset.id → dernière bougie (pour tous les assets)
        List<AssetDailyValue> latestCandles = assetDailyValueRepository.findLatestForAllAssets();
        Map<Long, AssetDailyValue> latestByAssetId = latestCandles.stream()
            .collect(Collectors.toMap(
                adv -> adv.getAsset().getId(),
                adv -> adv
            ));

        // Mappe chaque favori en AssetSummaryResponse, trié par symbol ASC
        List<AssetSummaryResponse> summaries = favorites.stream()
            .map(AccountFavoriteAsset::getAsset)
            .map(asset -> {
                AssetDailyValue latest = latestByAssetId.get(asset.getId());
                return AssetSummaryResponse.builder()
                    .symbol(asset.getSymbol())
                    .lastPrice(latest != null ? latest.getClose() : null)
                    .lastDate(latest != null ? latest.getDate() : null)
                    .build();
            })
            .sorted((a, b) -> a.getSymbol().compareTo(b.getSymbol()))
            .toList();

        log.info("Fetched {} favorites for account={}", summaries.size(), email);
        return summaries;
    }

    /*
        Ajoute un asset aux favoris de l'utilisateur.

        Flux :
          1. Charge le compte via email.
          2. Résout le symbol en Asset (ou 404).
          3. Vérifie que l'asset n'est pas déjà en favori (ou 409).
          4. Crée et sauvegarde le favori avec la date courante.

        Pas de @Transactional nécessaire ici : save() est une opération atomique.
    */
    public void addFavorite(String email, String symbol) {
        log.info("Adding favorite symbol={} for account={}", symbol, email);

        Account account = loadAccount(email);

        Asset asset = assetRepository.findBySymbol(symbol)
            .orElseThrow(() -> new AssetNotFoundException(symbol));

        if (favoriteRepository.existsByAccountAndAsset(account, asset)) {
            throw new FavoriteAlreadyExistsException(symbol);
        }

        AccountFavoriteAsset favorite = AccountFavoriteAsset.builder()
            .account(account)
            .asset(asset)
            .favoritedAt(LocalDateTime.now())
            .build();

        favoriteRepository.save(favorite);
        log.info("Added favorite symbol={} for account={}", symbol, email);
    }

    /*
        Retire un asset des favoris de l'utilisateur.

        Flux :
          1. Charge le compte via email.
          2. Résout le symbol en Asset (ou 404).
          3. Vérifie que l'asset est bien en favori (ou 404).
          4. Supprime la ligne physiquement.

        @Transactional : requis car deleteByAccountAndAsset() fait d'abord un
        SELECT puis un DELETE (Spring Data JPA "delete-by" pattern). Sans
        transaction, on risque une LazyInitializationException ou une
        TransactionRequiredException selon la configuration JPA.
    */
    @Transactional
    public void removeFavorite(String email, String symbol) {
        log.info("Removing favorite symbol={} for account={}", symbol, email);

        Account account = loadAccount(email);

        Asset asset = assetRepository.findBySymbol(symbol)
            .orElseThrow(() -> new AssetNotFoundException(symbol));

        if (!favoriteRepository.existsByAccountAndAsset(account, asset)) {
            throw new FavoriteNotFoundException(symbol);
        }

        favoriteRepository.deleteByAccountAndAsset(account, asset);
        log.info("Removed favorite symbol={} for account={}", symbol, email);
    }
}
