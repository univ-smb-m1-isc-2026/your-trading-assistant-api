package fr.info803.trading_assistant.dto.discord;

import lombok.Builder;

/**
 * Champ d'un Embed Discord.
 */
@Builder
public record DiscordField(
    String name,
    String value,
    boolean inline
) {}
