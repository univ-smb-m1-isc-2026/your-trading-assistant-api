package fr.info803.trading_assistant.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fr.info803.trading_assistant.dto.discord.DiscordMessage;
import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.Alert;
import fr.info803.trading_assistant.entity.AlertDirection;
import fr.info803.trading_assistant.entity.AlertType;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.TriggeredAlert;
import fr.info803.trading_assistant.event.AlertCreatedEvent;
import fr.info803.trading_assistant.event.AlertsTriggeredEvent;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertNotificationListener Unit Tests")
class AlertNotificationListenerTest {

    @Mock
    private DiscordNotificationService discordNotificationService;

    @InjectMocks
    private AlertNotificationListener alertNotificationListener;

    @Test
    @DisplayName("should send notification only to global webhook when user has no personal webhook")
    void shouldSendToGlobalWebhookOnlyWhenNoPersonalWebhook() {
        // Arrange
        Account account = Account.builder().email("user@example.com").build();
        Asset asset = Asset.builder().symbol("BTC").build();
        Alert alert = Alert.builder()
                .id(1L)
                .account(account)
                .asset(asset)
                .type(AlertType.PRICE_THRESHOLD)
                .direction(AlertDirection.ABOVE)
                .thresholdValue(BigDecimal.valueOf(50000))
                .recurring(true)
                .build();
        
        AlertCreatedEvent event = new AlertCreatedEvent(alert, account.getEmail());

        // Act
        alertNotificationListener.handleAlertCreated(event);

        // Assert
        verify(discordNotificationService, times(1)).sendMessage(any(DiscordMessage.class));
    }

    @Test
    @DisplayName("should send notification to both global and personal webhooks on alert created")
    void shouldSendToBothWebhooksOnAlertCreated() {
        // Arrange
        String personalWebhook = "https://discord.com/api/webhooks/123/abc";
        Account account = Account.builder()
                .email("user@example.com")
                .discordWebhook(personalWebhook)
                .build();
        Asset asset = Asset.builder().symbol("BTC").build();
        Alert alert = Alert.builder()
                .id(1L)
                .account(account)
                .asset(asset)
                .type(AlertType.PRICE_THRESHOLD)
                .direction(AlertDirection.ABOVE)
                .thresholdValue(BigDecimal.valueOf(50000))
                .recurring(true)
                .build();
        
        AlertCreatedEvent event = new AlertCreatedEvent(alert, account.getEmail());

        // Act
        alertNotificationListener.handleAlertCreated(event);

        // Assert
        verify(discordNotificationService, times(1)).sendMessage(any(DiscordMessage.class)); // Global
        verify(discordNotificationService, times(1)).sendMessage(any(DiscordMessage.class), eq(personalWebhook)); // Personal
    }

    @Test
    @DisplayName("should send notifications for triggered alerts to both webhooks")
    void shouldSendToBothWebhooksOnTriggeredAlerts() {
        // Arrange
        String personalWebhook = "https://discord.com/api/webhooks/123/abc";
        Account account = Account.builder()
                .email("user@example.com")
                .discordWebhook(personalWebhook)
                .build();
        Asset asset = Asset.builder().symbol("BTC").build();
        Alert alert = Alert.builder()
                .id(1L)
                .account(account)
                .asset(asset)
                .type(AlertType.PRICE_THRESHOLD)
                .direction(AlertDirection.ABOVE)
                .thresholdValue(BigDecimal.valueOf(50000))
                .recurring(true)
                .build();
                
        TriggeredAlert triggeredAlert = TriggeredAlert.builder()
                .id(100L)
                .alert(alert)
                .candleDate(LocalDate.now())
                .triggeredValue(BigDecimal.valueOf(51000))
                .build();

        AlertsTriggeredEvent event = new AlertsTriggeredEvent(List.of(triggeredAlert), LocalDate.now());

        // Act
        alertNotificationListener.handleAlertsTriggered(event);

        // Assert
        verify(discordNotificationService, times(1)).sendMessage(any(DiscordMessage.class)); // Global
        verify(discordNotificationService, times(1)).sendMessage(any(DiscordMessage.class), eq(personalWebhook)); // Personal
    }
}
