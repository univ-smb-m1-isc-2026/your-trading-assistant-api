package fr.info803.trading_assistant.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.info803.trading_assistant.dto.AuthResponse;
import fr.info803.trading_assistant.dto.LoginRequest;
import fr.info803.trading_assistant.dto.RegisterRequest;
import fr.info803.trading_assistant.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/*
    Contrôleur REST exposant les endpoints d'authentification.

    Ces routes sont publiques (pas de JWT requis) car elles servent
    justement à obtenir un token.

    @Valid déclenche la validation Jakarta des champs du DTO (ex: @NotBlank, @Email).
    Si la validation échoue, Spring retourne automatiquement un 400 Bad Request.
*/
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AccountService accountService;

    /*
        POST /auth/register
        Body  : { "username": "Jean", "email": "jean@email.com", "password": "motdepasse" }
        Retour: { "token": "eyJ..." }
    */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(accountService.register(request));
    }

    /*
        POST /auth/login
        Body  : { "email": "jean@email.com", "password": "motdepasse" }
        Retour: { "token": "eyJ..." }
    */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(accountService.login(request));
    }
}
