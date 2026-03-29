package fr.info803.trading_assistant.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import fr.info803.trading_assistant.config.DiscordProperties;
import fr.info803.trading_assistant.dto.discord.DiscordMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service pour l'envoi de notifications à Discord via Webhook.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordNotificationService {

    private final DiscordProperties discordProperties;
    private final WebClient.Builder webClientBuilder;

    /**
     * Envoie un message à Discord sur le webhook global.
     */
    public void sendMessage(DiscordMessage message) {
        sendMessage(message, discordProperties.getWebhookUrl());
    }

    /**
     * Envoie un message à Discord sur un webhook spécifique.
     */
    public void sendMessage(DiscordMessage message, String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("No webhook URL provided, skipping notification");
            return;
        }

        log.info("Sending notification to Discord webhook: {}", webhookUrl.replaceAll("(.{20}).*", "$1..."));
        
        webClientBuilder.build()
            .post()
            .uri(webhookUrl)
            .bodyValue(message)
            .retrieve()
            .toBodilessEntity()
            .subscribe(
                response -> log.debug("Discord notification sent successfully"),
                error -> log.error("Failed to send Discord notification: {}", error.getMessage())
            );
    }
}
