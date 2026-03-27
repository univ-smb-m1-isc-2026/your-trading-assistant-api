package fr.info803.trading_assistant.dto.discord;

import java.util.List;
import lombok.Builder;

/**
 * Représente un message envoyé à un Webhook Discord.
 */
@Builder
public record DiscordMessage(
    String content,
    String username,
    String avatar_url,
    boolean tts,
    List<DiscordEmbed> embeds
) {}
