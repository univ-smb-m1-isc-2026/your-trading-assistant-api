package fr.info803.trading_assistant.dto;

import fr.info803.trading_assistant.entity.Role;
import lombok.Builder;

@Builder
public record ProfileResponse(
    String username,
    String email,
    String discordWebhook,
    Role role
) {}
