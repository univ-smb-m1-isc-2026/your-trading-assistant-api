package fr.info803.trading_assistant.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import fr.info803.trading_assistant.dto.AssetSummaryResponse;
import fr.info803.trading_assistant.exception.AssetNotFoundException;
import fr.info803.trading_assistant.exception.FavoriteAlreadyExistsException;
import fr.info803.trading_assistant.exception.FavoriteNotFoundException;
import fr.info803.trading_assistant.exception.GlobalExceptionHandler;
import fr.info803.trading_assistant.service.FavoriteService;

/**
 * Unit tests for FavoriteController.
 *
 * Uses MockMvcBuilders.standaloneSetup() — no Spring context, no security infrastructure.
 * Only FavoriteController and GlobalExceptionHandler are registered. FavoriteService is mocked.
 *
 * Injecting the authenticated user:
 *   The controller method signature takes Authentication as a parameter.
 *   Spring MVC resolves it via PrincipalMethodArgumentResolver (Authentication extends Principal).
 *   In standaloneSetup, we pass a UsernamePasswordAuthenticationToken via .principal() on the
 *   MockMvcRequestBuilder. Spring MVC calls request.getUserPrincipal() and injects it directly.
 *
 * Covers:
 * - GET  /assets/favorites          : 200 with list, 200 with empty array
 * - POST /assets/{symbol}/favorite  : 204 on success, 404 for unknown symbol, 409 for duplicate
 * - DELETE /assets/{symbol}/favorite: 204 on success, 404 for unknown symbol, 404 if not favorited
 */
@DisplayName("FavoriteController Unit Tests")
@ExtendWith(MockitoExtension.class)
class FavoriteControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FavoriteService favoriteService;

    /*
        Authentication principal reused across all tests.
        UsernamePasswordAuthenticationToken is both an Authentication and a Principal,
        so it satisfies both the MockMvc .principal() call and the controller's
        Authentication parameter injection.
    */
    private UsernamePasswordAuthenticationToken userAuth;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new FavoriteController(favoriteService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        userAuth = new UsernamePasswordAuthenticationToken("test@example.com", null, List.of());
    }

    // =========================================================================
    // GET /assets/favorites
    // =========================================================================

    @Nested
    @DisplayName("GET /assets/favorites")
    class GetFavoritesTests {

        @Test
        @DisplayName("should return 200 with list of favorite asset summaries")
        void shouldReturn200WithFavoritesList() throws Exception {
            // Arrange
            List<AssetSummaryResponse> favorites = List.of(
                AssetSummaryResponse.builder()
                    .symbol("BTC")
                    .lastPrice(new BigDecimal("96000.50"))
                    .lastDate(LocalDate.of(2026, 2, 27))
                    .build(),
                AssetSummaryResponse.builder()
                    .symbol("ETH")
                    .lastPrice(new BigDecimal("3150.25"))
                    .lastDate(LocalDate.of(2026, 2, 27))
                    .build()
            );
            when(favoriteService.getFavorites("test@example.com")).thenReturn(favorites);

            // Act & Assert
            mockMvc.perform(get("/assets/favorites").principal(userAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].symbol").value("BTC"))
                .andExpect(jsonPath("$[0].lastPrice").isNumber())
                .andExpect(jsonPath("$[0].lastDate").exists())   // format delegated to Jackson autoconfiguration
                .andExpect(jsonPath("$[1].symbol").value("ETH"));
        }

        @Test
        @DisplayName("should return 200 with empty array when user has no favorites")
        void shouldReturn200WithEmptyListWhenNoFavorites() throws Exception {
            // Arrange
            when(favoriteService.getFavorites("test@example.com")).thenReturn(Collections.emptyList());

            // Act & Assert: empty favorites must return 200, not 204
            mockMvc.perform(get("/assets/favorites").principal(userAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
        }
    }

    // =========================================================================
    // POST /assets/{symbol}/favorite
    // =========================================================================

    @Nested
    @DisplayName("POST /assets/{symbol}/favorite")
    class AddFavoriteTests {

        @Test
        @DisplayName("should return 204 when favorite is successfully added")
        void shouldReturn204WhenFavoriteAdded() throws Exception {
            // Arrange: service returns normally (void)
            doNothing().when(favoriteService).addFavorite("test@example.com", "BTC");

            // Act & Assert
            mockMvc.perform(post("/assets/BTC/favorite").principal(userAuth))
                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("should return 404 with error JSON when symbol is unknown")
        void shouldReturn404WhenSymbolUnknown() throws Exception {
            // Arrange
            doThrow(new AssetNotFoundException("UNKNOWN"))
                .when(favoriteService).addFavorite("test@example.com", "UNKNOWN");

            // Act & Assert
            mockMvc.perform(post("/assets/UNKNOWN/favorite").principal(userAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Asset not found"))
                .andExpect(jsonPath("$.symbol").value("UNKNOWN"))
                .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("should return 409 with error JSON when asset is already in favorites")
        void shouldReturn409WhenAlreadyFavorited() throws Exception {
            // Arrange
            doThrow(new FavoriteAlreadyExistsException("BTC"))
                .when(favoriteService).addFavorite("test@example.com", "BTC");

            // Act & Assert
            mockMvc.perform(post("/assets/BTC/favorite").principal(userAuth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Asset already in favorites"))
                .andExpect(jsonPath("$.symbol").value("BTC"))
                .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    // =========================================================================
    // DELETE /assets/{symbol}/favorite
    // =========================================================================

    @Nested
    @DisplayName("DELETE /assets/{symbol}/favorite")
    class RemoveFavoriteTests {

        @Test
        @DisplayName("should return 204 when favorite is successfully removed")
        void shouldReturn204WhenFavoriteRemoved() throws Exception {
            // Arrange: service returns normally (void)
            doNothing().when(favoriteService).removeFavorite("test@example.com", "BTC");

            // Act & Assert
            mockMvc.perform(delete("/assets/BTC/favorite").principal(userAuth))
                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("should return 404 with error JSON when symbol is unknown")
        void shouldReturn404WhenSymbolUnknown() throws Exception {
            // Arrange
            doThrow(new AssetNotFoundException("UNKNOWN"))
                .when(favoriteService).removeFavorite("test@example.com", "UNKNOWN");

            // Act & Assert
            mockMvc.perform(delete("/assets/UNKNOWN/favorite").principal(userAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Asset not found"))
                .andExpect(jsonPath("$.symbol").value("UNKNOWN"))
                .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("should return 404 with error JSON when asset is not in favorites")
        void shouldReturn404WhenAssetNotInFavorites() throws Exception {
            // Arrange
            doThrow(new FavoriteNotFoundException("BTC"))
                .when(favoriteService).removeFavorite("test@example.com", "BTC");

            // Act & Assert
            mockMvc.perform(delete("/assets/BTC/favorite").principal(userAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Asset not in favorites"))
                .andExpect(jsonPath("$.symbol").value("BTC"))
                .andExpect(jsonPath("$.timestamp").exists());
        }
    }
}
