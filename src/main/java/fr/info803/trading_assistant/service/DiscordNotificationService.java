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
     * Envoie un message à Discord.
     */
    public void sendMessage(DiscordMessage message) {
        log.info("Sending notification to Discord");
        
        webClientBuilder.build()
            .post()
            .uri(discordProperties.getWebhookUrl())
            .bodyValue(message)
            .retrieve()
            .toBodilessEntity()
            .subscribe(
                response -> log.debug("Discord notification sent successfully"),
                error -> log.error("Failed to send Discord notification: {}", error.getMessage())
            );
    }
}
