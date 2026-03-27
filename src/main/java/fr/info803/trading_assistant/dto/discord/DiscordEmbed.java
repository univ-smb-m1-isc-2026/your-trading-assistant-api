package fr.info803.trading_assistant.dto.discord;

import java.util.List;
import lombok.Builder;

/**
 * Représente un "Embed" Discord pour des messages enrichis.
 */
@Builder
public record DiscordEmbed(
    String title,
    String type,
    String description,
    String url,
    Integer color,
    DiscordAuthor author,
    List<DiscordField> fields,
    DiscordFooter footer,
    DiscordImage image,
    DiscordThumbnail thumbnail
) {}
