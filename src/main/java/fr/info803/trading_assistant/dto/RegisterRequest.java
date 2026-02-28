package fr.info803.trading_assistant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/*
    DTO (Data Transfer Object) pour la requête d'inscription.

    Les annotations de validation Jakarta garantissent que les données
    reçues sont valides avant même d'atteindre le service.
    Spring renvoie automatiquement un 400 Bad Request si la validation échoue.
*/
@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    private String username;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    private String password;
}
