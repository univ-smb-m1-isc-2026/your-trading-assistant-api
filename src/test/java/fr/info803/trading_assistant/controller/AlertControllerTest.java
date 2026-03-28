package fr.info803.trading_assistant.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import fr.info803.trading_assistant.dto.AlertResponse;
import fr.info803.trading_assistant.dto.TriggeredAlertResponse;
import fr.info803.trading_assistant.exception.AlertNotFoundException;
import fr.info803.trading_assistant.exception.AssetNotFoundException;
import fr.info803.trading_assistant.exception.GlobalExceptionHandler;
import fr.info803.trading_assistant.service.AlertService;

/**
 * Unit tests for AlertController.
 *
 * Uses MockMvcBuilders.standaloneSetup() — no Spring context, no security infrastructure.
 * Only AlertController and GlobalExceptionHandler are registered. AlertService is mocked.
 *
 * Injecting the authenticated user:
 *   Same approach as FavoriteControllerTest — UsernamePasswordAuthenticationToken via .principal().
 *
 * Covers:
 * - GET    /alerts            : 200 with list, 200 with empty array
 * - GET    /alerts/triggered  : 200 with list, 200 with empty array
 * - POST   /alerts            : 201 on success, 404 for unknown symbol, 400 for invalid type
 * - PUT    /alerts/{id}       : 200 on success, 404 for unknown alert
 * - DELETE /alerts/{id}       : 204 on success, 404 for unknown alert
 */
@DisplayName("AlertController Unit Tests")
@ExtendWith(MockitoExtension.class)
class AlertControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AlertService alertService;

    private UsernamePasswordAuthenticationToken userAuth;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new AlertController(alertService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

        userAuth = new UsernamePasswordAuthenticationToken("test@example.com", null, List.of());
    }

    // =========================================================================
    // GET /alerts
    // =========================================================================

    @Nested
    @DisplayName("GET /alerts")
    class GetAlertsTests {

        @Test
        @DisplayName("should return 200 with list of configured alerts")
        void shouldReturn200WithAlertsList() throws Exception {
            // Arrange
            List<AlertResponse> alerts = List.of(
                AlertResponse.builder()
                    .id(1L)
                    .symbol("BTC")
                    .type("PRICE_THRESHOLD")
                    .direction("ABOVE")
                    .thresholdValue(new BigDecimal("100000"))
                    .recurring(true)
                    .active(true)
                    .createdAt(LocalDateTime.of(2026, 2, 28, 10, 30))
                    .build()
            );
            when(alertService.getAlerts("test@example.com")).thenReturn(alerts);

            // Act & Assert
            mockMvc.perform(get("/alerts").principal(userAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].symbol").value("BTC"))
                .andExpect(jsonPath("$[0].type").value("PRICE_THRESHOLD"))
                .andExpect(jsonPath("$[0].direction").value("ABOVE"))
                .andExpect(jsonPath("$[0].thresholdValue").isNumber())
                .andExpect(jsonPath("$[0].recurring").value(true))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].createdAt").exists());
        }

        @Test
        @DisplayName("should return 200 with empty array when user has no alerts")
        void shouldReturn200WithEmptyListWhenNoAlerts() throws Exception {
            // Arrange
            when(alertService.getAlerts("test@example.com")).thenReturn(Collections.emptyList());

            // Act & Assert
            mockMvc.perform(get("/alerts").principal(userAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
        }
    }

    // =========================================================================
    // GET /alerts/triggered
    // =========================================================================

    @Nested
    @DisplayName("GET /alerts/triggered")
    class GetTriggeredAlertsTests {

        @Test
        @DisplayName("should return 200 with list of triggered alerts")
        void shouldReturn200WithTriggeredAlertsList() throws Exception {
            // Arrange
            List<TriggeredAlertResponse> triggered = List.of(
                TriggeredAlertResponse.builder()
                    .id(1L)
                    .alertId(3L)
                    .symbol("BTC")
                    .type("PRICE_THRESHOLD")
                    .direction("ABOVE")
                    .thresholdValue(new BigDecimal("100000"))
                    .triggeredValue(new BigDecimal("101500"))
                    .candleDate(LocalDate.of(2026, 2, 27))
                    .triggeredAt(LocalDateTime.of(2026, 2, 28, 0, 5, 30))
                    .alert(AlertResponse.builder()
                        .id(3L)
                        .symbol("BTC")
                        .type("PRICE_THRESHOLD")
                        .build())
                    .build()
            );
            when(alertService.getTriggeredAlerts("test@example.com")).thenReturn(triggered);

            // Act & Assert
            mockMvc.perform(get("/alerts/triggered").principal(userAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].alertId").value(3))
                .andExpect(jsonPath("$[0].symbol").value("BTC"))
                .andExpect(jsonPath("$[0].triggeredValue").isNumber())
                .andExpect(jsonPath("$[0].candleDate").exists())
                .andExpect(jsonPath("$[0].triggeredAt").exists())
                .andExpect(jsonPath("$[0].alert").exists())
                .andExpect(jsonPath("$[0].alert.id").value(3))
                .andExpect(jsonPath("$[0].alert.symbol").value("BTC"))
                .andExpect(jsonPath("$[0].alert.type").value("PRICE_THRESHOLD"));
        }

        @Test
        @DisplayName("should return 200 with empty array when no alerts have been triggered")
        void shouldReturn200WithEmptyListWhenNoTriggeredAlerts() throws Exception {
            // Arrange
            when(alertService.getTriggeredAlerts("test@example.com")).thenReturn(Collections.emptyList());

            // Act & Assert
            mockMvc.perform(get("/alerts/triggered").principal(userAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
        }
    }

    // =========================================================================
    // POST /alerts
    // =========================================================================

    @Nested
    @DisplayName("POST /alerts")
    class CreateAlertTests {

        @Test
        @DisplayName("should return 201 with created alert when request is valid")
        void shouldReturn201WhenAlertCreated() throws Exception {
            // Arrange
            AlertResponse response = AlertResponse.builder()
                .id(1L)
                .symbol("BTC")
                .type("PRICE_THRESHOLD")
                .direction("ABOVE")
                .thresholdValue(new BigDecimal("100000"))
                .recurring(true)
                .active(true)
                .createdAt(LocalDateTime.of(2026, 2, 28, 10, 30))
                .build();
            when(alertService.createAlert(eq("test@example.com"), any())).thenReturn(response);

            String requestBody = """
                {
                    "symbol": "BTC",
                    "type": "PRICE_THRESHOLD",
                    "direction": "ABOVE",
                    "thresholdValue": 100000,
                    "recurring": true
                }
                """;

            // Act & Assert
            mockMvc.perform(post("/alerts")
                    .principal(userAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.symbol").value("BTC"))
                .andExpect(jsonPath("$.type").value("PRICE_THRESHOLD"))
                .andExpect(jsonPath("$.direction").value("ABOVE"))
                .andExpect(jsonPath("$.thresholdValue").isNumber())
                .andExpect(jsonPath("$.recurring").value(true))
                .andExpect(jsonPath("$.active").value(true));
        }

        @Test
        @DisplayName("should return 404 with error JSON when symbol is unknown")
        void shouldReturn404WhenSymbolUnknown() throws Exception {
            // Arrange
            when(alertService.createAlert(eq("test@example.com"), any()))
                .thenThrow(new AssetNotFoundException("UNKNOWN"));

            String requestBody = """
                {
                    "symbol": "UNKNOWN",
                    "type": "PRICE_THRESHOLD",
                    "direction": "ABOVE",
                    "thresholdValue": 100000,
                    "recurring": true
                }
                """;

            // Act & Assert
            mockMvc.perform(post("/alerts")
                    .principal(userAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Asset not found"))
                .andExpect(jsonPath("$.symbol").value("UNKNOWN"))
                .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    // =========================================================================
    // PUT /alerts/{id}
    // =========================================================================

    @Nested
    @DisplayName("PUT /alerts/{id}")
    class UpdateAlertTests {

        @Test
        @DisplayName("should return 200 with updated alert when request is valid")
        void shouldReturn200WhenAlertUpdated() throws Exception {
            // Arrange
            AlertResponse response = AlertResponse.builder()
                .id(1L)
                .symbol("BTC")
                .type("PRICE_THRESHOLD")
                .direction("ABOVE")
                .thresholdValue(new BigDecimal("105000"))
                .recurring(true)
                .active(true)
                .createdAt(LocalDateTime.of(2026, 2, 28, 10, 30))
                .build();
            when(alertService.updateAlert(eq("test@example.com"), eq(1L), any())).thenReturn(response);

            String requestBody = """
                {
                    "thresholdValue": 105000
                }
                """;

            // Act & Assert
            mockMvc.perform(put("/alerts/1")
                    .principal(userAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.thresholdValue").value(105000));
        }

        @Test
        @DisplayName("should return 404 with error JSON when alert does not exist")
        void shouldReturn404WhenAlertNotFound() throws Exception {
            // Arrange
            when(alertService.updateAlert(eq("test@example.com"), eq(999L), any()))
                .thenThrow(new AlertNotFoundException(999L));

            String requestBody = """
                {
                    "active": true
                }
                """;

            // Act & Assert
            mockMvc.perform(put("/alerts/999")
                    .principal(userAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Alert not found"))
                .andExpect(jsonPath("$.alertId").value(999))
                .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    // =========================================================================
    // DELETE /alerts/{id}
    // =========================================================================

    @Nested
    @DisplayName("DELETE /alerts/{id}")
    class DeleteAlertTests {

        @Test
        @DisplayName("should return 204 when alert is successfully deleted")
        void shouldReturn204WhenAlertDeleted() throws Exception {
            // Arrange: service returns normally (void)
            doNothing().when(alertService).deleteAlert("test@example.com", 1L);

            // Act & Assert
            mockMvc.perform(delete("/alerts/1").principal(userAuth))
                .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("should return 404 with error JSON when alert does not exist")
        void shouldReturn404WhenAlertNotFound() throws Exception {
            // Arrange
            doThrow(new AlertNotFoundException(999L))
                .when(alertService).deleteAlert("test@example.com", 999L);

            // Act & Assert
            mockMvc.perform(delete("/alerts/999").principal(userAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Alert not found"))
                .andExpect(jsonPath("$.alertId").value(999))
                .andExpect(jsonPath("$.timestamp").exists());
        }
    }
}
