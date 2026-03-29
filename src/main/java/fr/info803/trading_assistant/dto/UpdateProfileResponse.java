package fr.info803.trading_assistant.dto;

import lombok.Builder;

@Builder
public record UpdateProfileResponse(
    ProfileResponse profile,
    String token
) {}
