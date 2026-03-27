package fr.info803.trading_assistant.dto.discord;

import lombok.Builder;

/**
 * Pied de page d'un Embed Discord.
 */
@Builder
public record DiscordFooter(
    String text,
    String icon_url
) {}
