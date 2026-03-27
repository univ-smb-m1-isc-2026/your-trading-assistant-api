package fr.info803.trading_assistant.dto.discord;

import lombok.Builder;

/**
 * Image d'un Embed Discord.
 */
@Builder
public record DiscordImage(
    String url
) {}
