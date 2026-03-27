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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import fr.info803.trading_assistant.dto.AlertResponse;
import fr.info803.trading_assistant.dto.CreateAlertRequest;
import fr.info803.trading_assistant.dto.TriggeredAlertResponse;
import fr.info803.trading_assistant.dto.UpdateAlertRequest;
import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.Alert;
import fr.info803.trading_assistant.entity.AlertDirection;
import fr.info803.trading_assistant.entity.AlertType;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetDailyValue;
import fr.info803.trading_assistant.entity.AssetSource;
import fr.info803.trading_assistant.entity.Role;
import fr.info803.trading_assistant.entity.TriggeredAlert;
import fr.info803.trading_assistant.exception.AlertNotFoundException;
import fr.info803.trading_assistant.exception.AssetNotFoundException;
import fr.info803.trading_assistant.repository.AccountRepository;
import fr.info803.trading_assistant.repository.AlertRepository;
import fr.info803.trading_assistant.repository.AssetDailyValueRepository;
import fr.info803.trading_assistant.repository.AssetRepository;
import fr.info803.trading_assistant.repository.TriggeredAlertRepository;

/**
 * Unit tests for AlertService.
 *
 * Tests the business logic for alert management in isolation (no Spring context).
 * All dependencies are mocked using Mockito.
 *
 * Covers:
 * - getAlerts(): happy path, empty list
 * - getTriggeredAlerts(): happy path, empty list
 * - createAlert(): happy path, unknown symbol, invalid type, invalid direction
 * - updateAlert(): happy path with partial update, alert not found
 * - deleteAlert(): happy path (deletes triggered history + alert), alert not found
 * - evaluateAlerts(): happy path with trigger, no active alerts, no candle data,
 *   no evaluator, anti-duplicate, one-shot deactivation, recurring stays active,
 *   resilience on exception
 * - parseAlertType() / parseAlertDirection(): valid and invalid values
 */
@DisplayName("AlertService Unit Tests")
@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private TriggeredAlertRepository triggeredAlertRepository;

    @Mock
    private AssetDailyValueRepository assetDailyValueRepository;

    @Mock
    private AlertEvaluator priceEvaluator;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    /*
        On ne peut PAS utiliser @InjectMocks ici car Mockito ne sait pas injecter
        un mock dans un paramètre List<AlertEvaluator>. Il faut construire
        manuellement le service dans @BeforeEach en passant List.of(priceEvaluator).
    */
    private AlertService alertService;

    // ── shared fixtures ──────────────────────────────────────────────────────

    private static final String EMAIL = "test@example.com";
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 2, 27);

    private Account account;
    private AssetSource source;
    private Asset btcAsset;
    private Alert btcAlert;

    @BeforeEach
    void setUp() {
        // Construction manuelle pour injecter la liste d'évaluateurs
        alertService = new AlertService(
            accountRepository,
            assetRepository,
            alertRepository,
            triggeredAlertRepository,
            assetDailyValueRepository,
            List.of(priceEvaluator),
            eventPublisher
        );

        account = Account.builder()
            .id(1L)
            .email(EMAIL)
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

        btcAlert = Alert.builder()
            .id(1L)
            .account(account)
            .asset(btcAsset)
            .type(AlertType.PRICE_THRESHOLD)
            .direction(AlertDirection.ABOVE)
            .thresholdValue(new BigDecimal("100000"))
            .recurring(true)
            .active(true)
            .createdAt(LocalDateTime.of(2026, 2, 27, 10, 30))
            .build();
    }

    // =========================================================================
    // getAlerts()
    // =========================================================================

    @Nested
    @DisplayName("getAlerts()")
    class GetAlertsTests {

        @Test
        @DisplayName("should return list of alert responses for authenticated user")
        void shouldReturnAlertsList() {
            // Arrange
            when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
            when(alertRepository.findByAccount(account)).thenReturn(List.of(btcAlert));

            // Act
            List<AlertResponse> result = alertService.getAlerts(EMAIL);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(1L);
            assertThat(result.get(0).getSymbol()).isEqualTo("BTC");
            assertThat(result.get(0).getType()).isEqualTo("PRICE_THRESHOLD");
            assertThat(result.get(0).getDirection()).isEqualTo("ABOVE");
            assertThat(result.get(0).getThresholdValue()).isEqualByComparingTo(new BigDecimal("100000"));
            assertThat(result.get(0).isRecurring()).isTrue();
            assertThat(result.get(0).isActive()).isTrue();
        }

        @Test
        @DisplayName("should return empty list when user has no alerts")
        void shouldReturnEmptyListWhenNoAlerts() {
            // Arrange
            when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
            when(alertRepository.findByAccount(account)).thenReturn(Collections.emptyList());

            // Act
            List<AlertResponse> result = alertService.getAlerts(EMAIL);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should throw UsernameNotFoundException when account does not exist")
        void shouldThrowWhenAccountNotFound() {
            // Arrange
            when(accountRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> alertService.getAlerts("unknown@test.com"))
                .isInstanceOf(UsernameNotFoundException.class);
        }
    }

    // =========================================================================
    // getTriggeredAlerts()
    // =========================================================================

    @Nested
    @DisplayName("getTriggeredAlerts()")
    class GetTriggeredAlertsTests {

        @Test
        @DisplayName("should return triggered alert history for authenticated user")
        void shouldReturnTriggeredAlertsList() {
            // Arrange
            TriggeredAlert triggered = TriggeredAlert.builder()
                .id(1L)
                .alert(btcAlert)
                .triggeredValue(new BigDecimal("101500"))
                .candleDate(TEST_DATE)
                .triggeredAt(LocalDateTime.of(2026, 2, 28, 0, 5, 30))
                .build();

            when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
            when(triggeredAlertRepository.findByAlertAccountOrderByTriggeredAtDesc(account))
                .thenReturn(List.of(triggered));

            // Act
            List<TriggeredAlertResponse> result = alertService.getTriggeredAlerts(EMAIL);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(1L);
            assertThat(result.get(0).getAlertId()).isEqualTo(1L);
            assertThat(result.get(0).getSymbol()).isEqualTo("BTC");
            assertThat(result.get(0).getType()).isEqualTo("PRICE_THRESHOLD");
            assertThat(result.get(0).getDirection()).isEqualTo("ABOVE");
            assertThat(result.get(0).getThresholdValue()).isEqualByComparingTo(new BigDecimal("100000"));
            assertThat(result.get(0).getTriggeredValue()).isEqualByComparingTo(new BigDecimal("101500"));
            assertThat(result.get(0).getCandleDate()).isEqualTo(TEST_DATE);
        }

        @Test
        @DisplayName("should return empty list when no alerts have been triggered")
        void shouldReturnEmptyListWhenNoTriggeredAlerts() {
            // Arrange
            when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
            when(triggeredAlertRepository.findByAlertAccountOrderByTriggeredAtDesc(account))
                .thenReturn(Collections.emptyList());

            // Act
            List<TriggeredAlertResponse> result = alertService.getTriggeredAlerts(EMAIL);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // createAlert()
    // =========================================================================

    @Nested
    @DisplayName("createAlert()")
    class CreateAlertTests {

        @Test
        @DisplayName("should create alert with correct fields and return response")
        void shouldCreateAlertSuccessfully() {
            // Arrange
            CreateAlertRequest request = new CreateAlertRequest();
            request.setSymbol("BTC");
            request.setType("PRICE_THRESHOLD");
            request.setDirection("ABOVE");
            request.setThresholdValue(new BigDecimal("100000"));
            request.setRecurring(true);

            when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));
            when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
                Alert saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            // Act
            AlertResponse result = alertService.createAlert(EMAIL, request);

            // Assert
            assertThat(result.getId()).isEqualTo(1L);
            verify(eventPublisher).publishEvent(any(fr.info803.trading_assistant.event.AlertCreatedEvent.class));
            assertThat(result.getSymbol()).isEqualTo("BTC");
            assertThat(result.getType()).isEqualTo("PRICE_THRESHOLD");
            assertThat(result.getDirection()).isEqualTo("ABOVE");
            assertThat(result.getThresholdValue()).isEqualByComparingTo(new BigDecimal("100000"));
            assertThat(result.isRecurring()).isTrue();
            assertThat(result.isActive()).isTrue();

            // Verify save was called with correct entity
            ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
            verify(alertRepository).save(captor.capture());
            Alert saved = captor.getValue();
            assertThat(saved.getAccount()).isEqualTo(account);
            assertThat(saved.getAsset()).isEqualTo(btcAsset);
            assertThat(saved.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw AssetNotFoundException when symbol is unknown")
        void shouldThrowWhenSymbolUnknown() {
            // Arrange
            CreateAlertRequest request = new CreateAlertRequest();
            request.setSymbol("UNKNOWN");
            request.setType("PRICE_THRESHOLD");
            request.setDirection("ABOVE");
            request.setThresholdValue(new BigDecimal("100000"));
            request.setRecurring(true);

            when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
            when(assetRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> alertService.createAlert(EMAIL, request))
                .isInstanceOf(AssetNotFoundException.class);

            verify(alertRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when alert type is invalid")
        void shouldThrowWhenTypeInvalid() {
            // Arrange
            CreateAlertRequest request = new CreateAlertRequest();
            request.setSymbol("BTC");
            request.setType("INVALID_TYPE");
            request.setDirection("ABOVE");
            request.setThresholdValue(new BigDecimal("100000"));
            request.setRecurring(true);

            when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));

            // Act & Assert
            assertThatThrownBy(() -> alertService.createAlert(EMAIL, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid alert type")
                .hasMessageContaining("INVALID_TYPE");

            verify(alertRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when alert direction is invalid")
        void shouldThrowWhenDirectionInvalid() {
            // Arrange
            CreateAlertRequest request = new CreateAlertRequest();
            request.setSymbol("BTC");
            request.setType("PRICE_THRESHOLD");
            request.setDirection("SIDEWAYS");
            request.setThresholdValue(new BigDecimal("100000"));
            request.setRecurring(true);

            when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
            when(assetRepository.findBySymbol("BTC")).thenReturn(Optional.of(btcAsset));

            // Act & Assert
            assertThatThrownBy(() -> alertService.createAlert(EMAIL, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid alert direction")
                .hasMessageContaining("SIDEWAYS");

            verify(alertRepository, never()).save(any());
        }
    }

    // =========================================================================
    // updateAlert()
    // =========================================================================

    @Nested
    @DisplayName("updateAlert()")
    class UpdateAlertTests {

        @Test
        @DisplayName("should update only provided fields (partial update)")
        void shouldUpdateOnlyProvidedFields() {
            // Arrange: only update thresholdValue and active
            UpdateAlertRequest request = new UpdateAlertRequest();
            request.setThresholdValue(new BigDecimal("105000"));
            request.setActive(false);
            // type, direction, recurring are null → unchanged

            when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
            when(alertRepository.findByIdAndAccount(1L, account)).thenReturn(Optional.of(btcAlert));
            when(alertRepository.save(any(Alert.class))).thenReturn(btcAlert);

            // Act
            AlertResponse result = alertService.updateAlert(EMAIL, 1L, request);

            // Assert: modified fields
            ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
            verify(alertRepository).save(captor.capture());
            Alert saved = captor.getValue();
            assertThat(saved.getThresholdValue()).isEqualByComparingTo(new BigDecimal("105000"));
            assertThat(saved.isActive()).isFalse();
            // Assert: unchanged fields
            assertThat(saved.getType()).isEqualTo(AlertType.PRICE_THRESHOLD);
            assertThat(saved.getDirection()).isEqualTo(AlertDirection.ABOVE);
            assertThat(saved.isRecurring()).isTrue();
        }

        @Test
        @DisplayName("should throw AlertNotFoundException when alert does not exist or belongs to another user")
        void shouldThrowWhenAlertNotFound() {
            // Arrange
            UpdateAlertRequest request = new UpdateAlertRequest();
            request.setActive(true);

            when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
            when(alertRepository.findByIdAndAccount(999L, account)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> alertService.updateAlert(EMAIL, 999L, request))
                .isInstanceOf(AlertNotFoundException.class);

            verify(alertRepository, never()).save(any());
        }
    }

    // =========================================================================
    // deleteAlert()
    // =========================================================================

    @Nested
    @DisplayName("deleteAlert()")
    class DeleteAlertTests {

        @Test
        @DisplayName("should delete triggered history first, then the alert itself")
        void shouldDeleteTriggeredHistoryAndAlert() {
            // Arrange
            when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
            when(alertRepository.findByIdAndAccount(1L, account)).thenReturn(Optional.of(btcAlert));

            // Act
            alertService.deleteAlert(EMAIL, 1L);

            // Assert: triggered alerts deleted first (FK constraint), then the alert
            verify(triggeredAlertRepository).deleteByAlert(btcAlert);
            verify(alertRepository).delete(btcAlert);
        }

        @Test
        @DisplayName("should throw AlertNotFoundException when alert does not exist or belongs to another user")
        void shouldThrowWhenAlertNotFound() {
            // Arrange
            when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(account));
            when(alertRepository.findByIdAndAccount(999L, account)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> alertService.deleteAlert(EMAIL, 999L))
                .isInstanceOf(AlertNotFoundException.class);

            verify(triggeredAlertRepository, never()).deleteByAlert(any());
            verify(alertRepository, never()).delete(any());
        }
    }

    // =========================================================================
    // evaluateAlerts()
    // =========================================================================

    @Nested
    @DisplayName("evaluateAlerts()")
    class EvaluateAlertsTests {

        @Test
        @DisplayName("should do nothing when no active alerts exist")
        void shouldDoNothingWhenNoActiveAlerts() {
            // Arrange
            when(alertRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

            // Act
            alertService.evaluateAlerts(TEST_DATE);

            // Assert: no further interactions
            verify(assetDailyValueRepository, never()).findByAssetAndDate(any(), any());
        }
    }

    // =========================================================================
    // evaluateSingleAlert()
    // =========================================================================

    @Nested
    @DisplayName("evaluateSingleAlert()")
    class EvaluateSingleAlertTests {

        @Test
        @DisplayName("should trigger alert when evaluator returns a value and create triggered record")
        void shouldTriggerAlertWhenConditionMet() {
            // Arrange
            AssetDailyValue candle = buildCandle(btcAsset, TEST_DATE);

            when(priceEvaluator.supports(AlertType.PRICE_THRESHOLD)).thenReturn(true);
            when(assetDailyValueRepository.findByAssetAndDate(btcAsset, TEST_DATE))
                .thenReturn(Optional.of(candle));
            when(priceEvaluator.evaluate(btcAlert, candle))
                .thenReturn(Optional.of(new BigDecimal("101000")));
            when(triggeredAlertRepository.existsByAlertAndCandleDate(btcAlert, TEST_DATE))
                .thenReturn(false);
            when(triggeredAlertRepository.save(any(TriggeredAlert.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Optional<TriggeredAlert> result = alertService.evaluateSingleAlert(btcAlert, TEST_DATE);

            // Assert
            assertThat(result).isPresent();
            TriggeredAlert saved = result.get();
            assertThat(saved.getAlert()).isEqualTo(btcAlert);
            assertThat(saved.getTriggeredValue()).isEqualByComparingTo(new BigDecimal("101000"));
            assertThat(saved.getCandleDate()).isEqualTo(TEST_DATE);
            assertThat(saved.getTriggeredAt()).isNotNull();
            
            verify(triggeredAlertRepository).save(any(TriggeredAlert.class));
        }

        @Test
        @DisplayName("should return false when no candle exists for the alert's asset on that date")
        void shouldReturnFalseWhenNoCandleExists() {
            // Arrange
            when(priceEvaluator.supports(AlertType.PRICE_THRESHOLD)).thenReturn(true);
            when(assetDailyValueRepository.findByAssetAndDate(btcAsset, TEST_DATE))
                .thenReturn(Optional.empty());

            // Act
            Optional<TriggeredAlert> result = alertService.evaluateSingleAlert(btcAlert, TEST_DATE);

            // Assert
            assertThat(result).isEmpty();
            verify(triggeredAlertRepository, never()).save(any());
        }

        @Test
        @DisplayName("should return false when evaluator returns empty (condition not met)")
        void shouldReturnFalseWhenConditionNotMet() {
            // Arrange
            AssetDailyValue candle = buildCandle(btcAsset, TEST_DATE);

            when(priceEvaluator.supports(AlertType.PRICE_THRESHOLD)).thenReturn(true);
            when(assetDailyValueRepository.findByAssetAndDate(btcAsset, TEST_DATE))
                .thenReturn(Optional.of(candle));
            when(priceEvaluator.evaluate(btcAlert, candle)).thenReturn(Optional.empty());

            // Act
            Optional<TriggeredAlert> result = alertService.evaluateSingleAlert(btcAlert, TEST_DATE);

            // Assert
            assertThat(result).isEmpty();
            verify(triggeredAlertRepository, never()).save(any());
        }

        @Test
        @DisplayName("should skip trigger when alert was already triggered for this date (anti-duplicate)")
        void shouldSkipWhenAlreadyTriggeredForDate() {
            // Arrange
            AssetDailyValue candle = buildCandle(btcAsset, TEST_DATE);

            when(priceEvaluator.supports(AlertType.PRICE_THRESHOLD)).thenReturn(true);
            when(assetDailyValueRepository.findByAssetAndDate(btcAsset, TEST_DATE))
                .thenReturn(Optional.of(candle));
            when(priceEvaluator.evaluate(btcAlert, candle))
                .thenReturn(Optional.of(new BigDecimal("101000")));
            when(triggeredAlertRepository.existsByAlertAndCandleDate(btcAlert, TEST_DATE))
                .thenReturn(true);

            // Act
            Optional<TriggeredAlert> result = alertService.evaluateSingleAlert(btcAlert, TEST_DATE);


            // Assert
            assertThat(result).isEmpty();
            verify(triggeredAlertRepository, never()).save(any());
        }

        @Test
        @DisplayName("should deactivate one-shot alert after trigger (recurring=false)")
        void shouldDeactivateOneShotAlertAfterTrigger() {
            // Arrange: one-shot alert (recurring=false)
            Alert oneShotAlert = Alert.builder()
                .id(2L)
                .account(account)
                .asset(btcAsset)
                .type(AlertType.PRICE_THRESHOLD)
                .direction(AlertDirection.ABOVE)
                .thresholdValue(new BigDecimal("100000"))
                .recurring(false) // one-shot
                .active(true)
                .createdAt(LocalDateTime.of(2026, 2, 27, 10, 30))
                .build();

            AssetDailyValue candle = buildCandle(btcAsset, TEST_DATE);

            when(priceEvaluator.supports(AlertType.PRICE_THRESHOLD)).thenReturn(true);
            when(assetDailyValueRepository.findByAssetAndDate(btcAsset, TEST_DATE))
                .thenReturn(Optional.of(candle));
            when(priceEvaluator.evaluate(oneShotAlert, candle))
                .thenReturn(Optional.of(new BigDecimal("101000")));
            when(triggeredAlertRepository.existsByAlertAndCandleDate(oneShotAlert, TEST_DATE))
                .thenReturn(false);
            when(triggeredAlertRepository.save(any(TriggeredAlert.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Optional<TriggeredAlert> result = alertService.evaluateSingleAlert(oneShotAlert, TEST_DATE);

            // Assert
            assertThat(result).isPresent();
            assertThat(oneShotAlert.isActive()).isFalse(); // deactivated
            // save called twice: once for triggered alert, once for deactivated alert
            verify(alertRepository).save(oneShotAlert);
        }

        @Test
        @DisplayName("should keep recurring alert active after trigger (recurring=true)")
        void shouldKeepRecurringAlertActiveAfterTrigger() {
            // Arrange: recurring alert
            AssetDailyValue candle = buildCandle(btcAsset, TEST_DATE);

            when(priceEvaluator.supports(AlertType.PRICE_THRESHOLD)).thenReturn(true);
            when(assetDailyValueRepository.findByAssetAndDate(btcAsset, TEST_DATE))
                .thenReturn(Optional.of(candle));
            when(priceEvaluator.evaluate(btcAlert, candle))
                .thenReturn(Optional.of(new BigDecimal("101000")));
            when(triggeredAlertRepository.existsByAlertAndCandleDate(btcAlert, TEST_DATE))
                .thenReturn(false);
            when(triggeredAlertRepository.save(any(TriggeredAlert.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Optional<TriggeredAlert> result = alertService.evaluateSingleAlert(btcAlert, TEST_DATE);

            // Assert
            assertThat(result).isPresent();
            assertThat(btcAlert.isActive()).isTrue(); // still active
            verify(alertRepository, never()).save(any()); // alert not re-saved
        }

        @Test
        @DisplayName("should return false when no evaluator supports the alert type")
        void shouldReturnFalseWhenNoEvaluatorSupportsType() {
            // Arrange: evaluator does not support this type
            when(priceEvaluator.supports(AlertType.PRICE_THRESHOLD)).thenReturn(false);

            // Act
            Optional<TriggeredAlert> result = alertService.evaluateSingleAlert(btcAlert, TEST_DATE);

            // Assert
            assertThat(result).isEmpty();
            verify(triggeredAlertRepository, never()).save(any());
        }



    }

    // =========================================================================
    // parseAlertType() / parseAlertDirection()
    // =========================================================================

    @Nested
    @DisplayName("parseAlertType() and parseAlertDirection()")
    class ParseEnumTests {

        @Test
        @DisplayName("should parse valid AlertType values")
        void shouldParseValidAlertType() {
            assertThat(alertService.parseAlertType("PRICE_THRESHOLD")).isEqualTo(AlertType.PRICE_THRESHOLD);
            assertThat(alertService.parseAlertType("VOLUME_THRESHOLD")).isEqualTo(AlertType.VOLUME_THRESHOLD);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for invalid AlertType")
        void shouldThrowForInvalidAlertType() {
            assertThatThrownBy(() -> alertService.parseAlertType("INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid alert type")
                .hasMessageContaining("INVALID");
        }

        @Test
        @DisplayName("should parse valid AlertDirection values")
        void shouldParseValidAlertDirection() {
            assertThat(alertService.parseAlertDirection("ABOVE")).isEqualTo(AlertDirection.ABOVE);
            assertThat(alertService.parseAlertDirection("BELOW")).isEqualTo(AlertDirection.BELOW);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for invalid AlertDirection")
        void shouldThrowForInvalidAlertDirection() {
            assertThatThrownBy(() -> alertService.parseAlertDirection("SIDEWAYS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid alert direction")
                .hasMessageContaining("SIDEWAYS");
        }
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private AssetDailyValue buildCandle(Asset asset, LocalDate date) {
        return AssetDailyValue.builder()
            .id(1L)
            .asset(asset)
            .date(date)
            .open(new BigDecimal("95000"))
            .high(new BigDecimal("101000"))
            .low(new BigDecimal("94000"))
            .close(new BigDecimal("99000"))
            .volume(new BigDecimal("50000"))
            .build();
    }
}
