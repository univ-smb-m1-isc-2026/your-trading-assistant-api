package fr.info803.trading_assistant.dto.discord;

import lombok.Builder;

/**
 * Miniature d'un Embed Discord.
 */
@Builder
public record DiscordThumbnail(
    String url
) {}
