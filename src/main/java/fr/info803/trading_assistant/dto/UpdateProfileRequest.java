package fr.info803.trading_assistant.dto;

import jakarta.validation.constraints.Email;
import lombok.Builder;

@Builder
public record UpdateProfileRequest(
    String username,
    
    @Email(message = "L'email doit être valide")
    String email,
    
    String oldPassword,
    
    String newPassword,
    
    String discordWebhook
) {}
