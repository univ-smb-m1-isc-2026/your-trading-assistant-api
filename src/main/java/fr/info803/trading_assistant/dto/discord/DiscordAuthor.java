package fr.info803.trading_assistant.dto.discord;

import lombok.Builder;

/**
 * Auteur d'un Embed Discord.
 */
@Builder
public record DiscordAuthor(
    String name,
    String url,
    String icon_url
) {}
