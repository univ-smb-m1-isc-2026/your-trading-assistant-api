package fr.info803.trading_assistant.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/*
    DTO pour la réponse d'authentification (register et login).
    Retourne uniquement le token JWT au frontend.
    Le frontend peut décoder le token côté client pour lire id, email et username.
*/
@Getter
@Setter
@Builder
public class AuthResponse {

    private String token;
}
