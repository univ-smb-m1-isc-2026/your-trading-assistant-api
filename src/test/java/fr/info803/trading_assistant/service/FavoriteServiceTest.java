package fr.info803.trading_assistant.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fr.info803.trading_assistant.dto.AssetSummaryResponse;
import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.AccountFavoriteAsset;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetDailyValue;
import fr.info803.trading_assistant.entity.AssetSource;
import fr.info803.trading_assistant.entity.Role;
import fr.info803.trading_assistant.exception.AssetNotFoundException;
import fr.info803.trading_assistant.exception.FavoriteAlreadyExistsException;
import fr.info803.trading_assistant.exception.FavoriteNotFoundException;
import fr.info803.trading_assistant.repository.AccountFavoriteAssetRepository;
import fr.info803.trading_assistant.repository.AccountRepository;
import fr.info803.trading_assistant.repository.AssetDailyValueRepository;
import fr.info803.trading_assistant.repository.AssetRepository;

/**
 * Unit tests for FavoriteService.
 *
 * Tests the business logic for favorite management in isolation (no Spring context).
 * All dependencies are mocked using Mockito.
 *
 * Covers:
 * - getFavorites(): happy path with prices, empty list short-circuit, alphabetical sort,
 *   null prices when no candle exists
 * - addFavorite(): happy path with saved entity, unknown symbol, duplicate favorite
 * - removeFavorite(): happy path, unknown symbol, not in favorites
 */
@DisplayName("FavoriteService Unit Tests")
@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AccountFavoriteAssetRepository favoriteRepository;

    @Mock
    private AssetDailyValueRepository assetDailyValueRepository;

    @InjectMocks
    private FavoriteService favoriteService;

    // ── shared fixtures ──────────────────────────────────────────────────────

    private Account account;
    private AssetSource source;
    private Asset btcAsset;
    private Asset ethAsset;

    @BeforeEach
    void setUp() {
        account = Account.builder()
            .id(1L)
            .email("test@example.com")
            .username("testuser")
            .password("hashed")
            .role(Role.ROLE_USER)
            .build();

        source = AssetSource.builder()
            .id(1L)
            .name("hyperliquid")
            .url("https://api.hyperliquid.xyz/info")
            .build();

        btcAsset = Asset.builder().id(10L).symbol("BTC").source(source).build();
        ethAsset = Asset.builder().id(11L).symbol("ETH").source(source).build();

        // loadAccount() est appelé par chaque méthode publique
        when(accountRepository.findByEmail("test@example.com")).thenReturn(Optional.of(account));
    }

    // =========================================================================
    // getFavorites()
    // =========================================================================

    @Nested
    @DisplayName("getFavorites()")
    class GetFavoritesTests {

        @Test
        @DisplayName("should return favorites with their latest prices")
        void shouldReturnFavoritesWithLatestPrices() {
            // Arrange
            AccountFavoriteAsset btcFav = buildFavorite(1L, account, btcAsset);
            AccountFavoriteAsset ethFav = buildFavorite(2L, account, ethAsset);
            when(favoriteRepository.findByAccount(account)).thenReturn(List.of(btcFav, ethFav));

            AssetDailyValue btcCandle = buildCandle(1L, btcAsset, LocalDate.of(2026, 2, 27), "96000");
            AssetDailyValue ethCandle = buildCandle(2L, ethAsset, LocalDate.of(2026, 2, 27), "3150");
            when(assetDailyValueRepository.findLatestForAllAssets()).thenReturn(List.of(btcCandle, ethCandle));

            // Act
            List<AssetSummaryResponse> result = favoriteService.getFavorites("test@example.com");

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result).extracting(AssetSummaryResponse::getSymbol).containsExactly("BTC", "ETH");
            assertThat(result.get(0).getLastPrice()).isEqualByComparingTo(new BigDecimal("96000"));
            assertThat(result.get(1).getLastPrice()).isEqualByComparingTo(new BigDecimal("3150"));
        }

        @Test
        @DisplayName("should return empty list and skip candle query when user has no favorites")
        void shouldReturnEmptyListAndSkipCandleQueryWhenNoFavorites() {
            // Arrange
            when(favoriteRepository.findByAccount(account)).thenReturn(Collections.emptyList());

            // Act
            List<AssetSummaryResponse> result = favoriteService.getFavorites("test@example.com");

            // Assert: empty result, and no needless query to the candle repository
            assertThat(result).isEmpty();
            verify(assetDailyValueRepository, never()).findLatestForAllAssets();
        }

        @Test
        @DisplayName("should sort favorites alphabetically by symbol")
        void shouldSortFavoritesAlphabetically() {
            // Arrange: favorites returned in reverse order — service must sort
            Asset aeroAsset = Asset.builder().id(12L).symbol("AERO").source(source).build();
            AccountFavoriteAsset ethFav  = buildFavorite(1L, account, ethAsset);
            AccountFavoriteAsset aeroFav = buildFavorite(2L, account, aeroAsset);
            AccountFavoriteAsset btcFav  = buildFavorite(3L, account, btcAsset);
            when(favoriteRepository.findByAccount(account)).thenReturn(List.of(ethFav, aeroFav, btcFav));
            when(assetDailyValueRepository.findLatestForAllAssets()).thenReturn(Collections.emptyList());

            // Act
            List<AssetSummaryResponse> result = favoriteService.getFavorites("test@example.com");

            // Assert: alphabetical order regardless of insertion order
            assertThat(result).extracting(AssetSummaryResponse::getSymbol)
                .containsExactly("AERO", "BTC", "ETH");
        }

        @Test
        @DisplayName("should return null prices when no candle exists for a favorited asset")
        void shouldReturnNullPricesWhenNoCandleExists() {
            // Arrange: BTC is favorited but has no candle data
            AccountFavoriteAsset btcFav = buildFavorite(1L, account, btcAsset);
            when(favoriteRepository.findByAccount(account)).thenReturn(List.of(btcFav));
            when(assetDailyValueRepository.findLatestForAllAssets()).thenReturn(Collections.emptyList());

            // Act
            List<AssetSummaryResponse> result = favoriteService.getFavorites("test@example.com");

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSymbol()).isEqualTo("BTC");
            assertThat(result.get(0).getLastPrice()).isNull();
            assertThat(result.get(0).getLastDate()).isNull();
        }
    }

    // =========================================================================
    // addFavorite()
    // =========================================================================

    @Nested
    @DisplayName("addFavorite()")
    class AddFavoriteTests {

        @Test
        @DisplayName("should save favorite with correct account, asset and non-null favoritedAt")
        void shouldSaveFavoriteWithCorrectFields() {
            // Arrange
            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(favoriteRepository.existsByAccountAndAsset(account, btcAsset)).thenReturn(false);

            // Act
            favoriteService.addFavorite("test@example.com", "BTC");

            // Assert: capture what was passed to save() and verify every field
            ArgumentCaptor<AccountFavoriteAsset> captor = ArgumentCaptor.forClass(AccountFavoriteAsset.class);
            verify(favoriteRepository).save(captor.capture());

            AccountFavoriteAsset saved = captor.getValue();
            assertThat(saved.getAccount()).isEqualTo(account);
            assertThat(saved.getAsset()).isEqualTo(btcAsset);
            assertThat(saved.getFavoritedAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw AssetNotFoundException when symbol is unknown")
        void shouldThrowAssetNotFoundExceptionWhenSymbolUnknown() {
            // Arrange
            when(assetRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> favoriteService.addFavorite("test@example.com", "UNKNOWN"))
                .isInstanceOf(AssetNotFoundException.class)
                .hasMessageContaining("UNKNOWN");

            // No save must happen after the lookup failure
            verify(favoriteRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw FavoriteAlreadyExistsException when asset is already favorited")
        void shouldThrowFavoriteAlreadyExistsExceptionWhenAlreadyFavorited() {
            // Arrange: asset exists but is already in favorites
            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(favoriteRepository.existsByAccountAndAsset(account, btcAsset)).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> favoriteService.addFavorite("test@example.com", "BTC"))
                .isInstanceOf(FavoriteAlreadyExistsException.class)
                .hasMessageContaining("BTC");

            verify(favoriteRepository, never()).save(any());
        }
    }

    // =========================================================================
    // removeFavorite()
    // =========================================================================

    @Nested
    @DisplayName("removeFavorite()")
    class RemoveFavoriteTests {

        @Test
        @DisplayName("should delete the favorite when it exists")
        void shouldDeleteFavoriteWhenItExists() {
            // Arrange
            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(favoriteRepository.existsByAccountAndAsset(account, btcAsset)).thenReturn(true);

            // Act
            favoriteService.removeFavorite("test@example.com", "BTC");

            // Assert: deleteByAccountAndAsset must be called exactly once with correct args
            verify(favoriteRepository).deleteByAccountAndAsset(account, btcAsset);
        }

        @Test
        @DisplayName("should throw AssetNotFoundException when symbol is unknown")
        void shouldThrowAssetNotFoundExceptionWhenSymbolUnknown() {
            // Arrange
            when(assetRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> favoriteService.removeFavorite("test@example.com", "UNKNOWN"))
                .isInstanceOf(AssetNotFoundException.class)
                .hasMessageContaining("UNKNOWN");

            verify(favoriteRepository, never()).deleteByAccountAndAsset(any(), any());
        }

        @Test
        @DisplayName("should throw FavoriteNotFoundException when asset is not in favorites")
        void shouldThrowFavoriteNotFoundExceptionWhenNotFavorited() {
            // Arrange: asset exists but is NOT in favorites
            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(favoriteRepository.existsByAccountAndAsset(account, btcAsset)).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> favoriteService.removeFavorite("test@example.com", "BTC"))
                .isInstanceOf(FavoriteNotFoundException.class)
                .hasMessageContaining("BTC");

            verify(favoriteRepository, never()).deleteByAccountAndAsset(any(), any());
        }
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private AccountFavoriteAsset buildFavorite(Long id, Account acct, Asset asset) {
        return AccountFavoriteAsset.builder()
            .id(id)
            .account(acct)
            .asset(asset)
            .favoritedAt(LocalDateTime.now())
            .build();
    }

    private AssetDailyValue buildCandle(Long id, Asset asset, LocalDate date, String close) {
        return AssetDailyValue.builder()
            .id(id)
            .asset(asset)
            .date(date)
            .open(BigDecimal.ZERO)
            .high(BigDecimal.ZERO)
            .low(BigDecimal.ZERO)
            .close(new BigDecimal(close))
            .volume(BigDecimal.ZERO)
            .build();
    }
}
