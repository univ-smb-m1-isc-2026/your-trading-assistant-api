package fr.info803.trading_assistant.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.Alert;
import fr.info803.trading_assistant.entity.TriggeredAlert;
import fr.info803.trading_assistant.dto.discord.DiscordEmbed;
import fr.info803.trading_assistant.dto.discord.DiscordField;
import fr.info803.trading_assistant.dto.discord.DiscordMessage;
import fr.info803.trading_assistant.event.AlertCreatedEvent;
import fr.info803.trading_assistant.event.AlertsTriggeredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listener pour les événements liés aux alertes.
 * Formate et envoie des notifications Discord.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertNotificationListener {

    private final DiscordNotificationService discordNotificationService;

    // Couleurs Discord (Entier hexadécimal)
    private static final int COLOR_BLUE = 0x3498DB;
    private static final int COLOR_GOLD = 0xF1C40F;

    /**
     * Notifie la création d'une nouvelle alerte.
     */
    @EventListener
    public void handleAlertCreated(AlertCreatedEvent event) {
        Alert alert = event.alert();
        log.info("Handling AlertCreatedEvent for alert id={}", alert.getId());

        DiscordEmbed embed = DiscordEmbed.builder()
            .title("🔔 Nouvelle Alerte Configurée")
            .color(COLOR_BLUE)
            .description("Une nouvelle alerte a été ajoutée à votre compte.")
            .fields(List.of(
                DiscordField.builder().name("Actif").value(alert.getAsset().getSymbol()).inline(true).build(),
                DiscordField.builder().name("Type").value(alert.getType().name()).inline(true).build(),
                DiscordField.builder().name("Condition").value(alert.getDirection().name() + " " + alert.getThresholdValue()).inline(false).build(),
                DiscordField.builder().name("Récurrente").value(alert.isRecurring() ? "Oui" : "Non").inline(true).build()
            ))
            .footer(fr.info803.trading_assistant.dto.discord.DiscordFooter.builder().text("Utilisateur : " + event.accountEmail()).build())
            .build();

        discordNotificationService.sendMessage(DiscordMessage.builder()
            .embeds(List.of(embed))
            .build());
    }

    /**
     * Notifie les alertes déclenchées, groupées par utilisateur.
     */
    @EventListener
    public void handleAlertsTriggered(AlertsTriggeredEvent event) {
        List<TriggeredAlert> triggeredAlerts = event.triggeredAlerts();
        log.info("Handling AlertsTriggeredEvent for {} alerts", triggeredAlerts.size());

        // Groupement par compte (Account)
        Map<Account, List<TriggeredAlert>> alertsByAccount = triggeredAlerts.stream()
            .collect(Collectors.groupingBy(ta -> ta.getAlert().getAccount()));

        alertsByAccount.forEach((account, alerts) -> {
            List<DiscordField> fields = alerts.stream()
                .map(ta -> {
                    Alert alert = ta.getAlert();
                    String name = "🚀 " + alert.getAsset().getSymbol();
                    String value = String.format("**Déclenché à : %s**\n(Seuil %s : %s)",
                        ta.getTriggeredValue(),
                        alert.getDirection().name(),
                        alert.getThresholdValue());
                    return DiscordField.builder().name(name).value(value).inline(false).build();
                })
                .toList();

            DiscordEmbed embed = DiscordEmbed.builder()
                .title("🔥 Alertes Déclenchées !")
                .color(COLOR_GOLD)
                .description("Le marché a atteint vos objectifs pour la date du " + event.date())
                .fields(fields)
                .footer(fr.info803.trading_assistant.dto.discord.DiscordFooter.builder().text("Compte : " + account.getEmail()).build())
                .build();

            discordNotificationService.sendMessage(DiscordMessage.builder()
                .embeds(List.of(embed))
                .build());
        });
    }
}
