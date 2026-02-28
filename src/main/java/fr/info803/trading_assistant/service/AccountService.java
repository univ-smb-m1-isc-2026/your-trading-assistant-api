package fr.info803.trading_assistant.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import fr.info803.trading_assistant.dto.AuthResponse;
import fr.info803.trading_assistant.dto.LoginRequest;
import fr.info803.trading_assistant.dto.RegisterRequest;
import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.Role;
import fr.info803.trading_assistant.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
    Service gérant l'inscription et la connexion des comptes.

    N'implémente PAS UserDetailsService
    Le UserDetailsService est déclaré séparément dans ApplicationConfig.

    @RequiredArgsConstructor génère un constructeur avec tous les champs "final",
    ce qui déclenche l'injection de dépendances par Spring (injection par constructeur).
    C'est la méthode recommandée car elle garantit l'immutabilité et facilite les tests.
*/
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    /*
        Inscription d'un nouveau compte.
        Le mot de passe est encodé en BCrypt avant d'être stocké.
        Par défaut, tout nouveau compte reçoit le rôle ROLE_USER.
    */
    public AuthResponse register(RegisterRequest request) {
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Un compte existe déjà avec cet email");
        }

        Account account = Account.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.ROLE_USER)
                .build();

        accountRepository.save(account);
        log.info("Nouveau compte créé : {}", account.getEmail());

        String token = jwtService.generateToken(account);
        return AuthResponse.builder().token(token).build();
    }

    /*
        Connexion d'un compte existant.
        AuthenticationManager délègue à DaoAuthenticationProvider qui :
          1. Charge le compte via UserDetailsService (déclaré dans ApplicationConfig)
          2. Compare le mot de passe fourni avec le hash BCrypt stocké
          3. Lance une BadCredentialsException si les credentials sont invalides
    */
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        Account account = accountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Compte introuvable"));

        log.info("Connexion réussie : {}", account.getEmail());

        String token = jwtService.generateToken(account);
        return AuthResponse.builder().token(token).build();
    }
}
