package fr.info803.trading_assistant.service;

import java.util.List;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.info803.trading_assistant.dto.AuthResponse;
import fr.info803.trading_assistant.dto.LoginRequest;
import fr.info803.trading_assistant.dto.ProfileResponse;
import fr.info803.trading_assistant.dto.RegisterRequest;
import fr.info803.trading_assistant.dto.UpdateProfileRequest;
import fr.info803.trading_assistant.dto.UpdateProfileResponse;
import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.Alert;
import fr.info803.trading_assistant.entity.Role;
import fr.info803.trading_assistant.repository.AccountFavoriteAssetRepository;
import fr.info803.trading_assistant.repository.AccountRepository;
import fr.info803.trading_assistant.repository.AlertRepository;
import fr.info803.trading_assistant.repository.TriggeredAlertRepository;
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
    private final AlertRepository alertRepository;
    private final TriggeredAlertRepository triggeredAlertRepository;
    private final AccountFavoriteAssetRepository accountFavoriteAssetRepository;

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

    public ProfileResponse getProfile(Account account) {
        return ProfileResponse.builder()
                .username(account.getDisplayUsername())
                .email(account.getEmail())
                .discordWebhook(account.getDiscordWebhook())
                .role(account.getRole())
                .build();
    }

    @Transactional
    public UpdateProfileResponse updateProfile(Account account, UpdateProfileRequest request) {
        boolean tokenNeedsRefresh = false;

        if (request.email() != null && !request.email().equals(account.getEmail())) {
            if (accountRepository.existsByEmail(request.email())) {
                throw new IllegalArgumentException("Cet email est déjà utilisé.");
            }
            account.setEmail(request.email());
            tokenNeedsRefresh = true;
        }

        if (request.username() != null && !request.username().equals(account.getDisplayUsername())) {
            account.setUsername(request.username());
            tokenNeedsRefresh = true;
        }

        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            if (request.oldPassword() == null || !passwordEncoder.matches(request.oldPassword(), account.getPassword())) {
                throw new IllegalArgumentException("L'ancien mot de passe est incorrect.");
            }
            account.setPassword(passwordEncoder.encode(request.newPassword()));
            tokenNeedsRefresh = true;
        }

        if (request.discordWebhook() != null) {
            account.setDiscordWebhook(request.discordWebhook());
        }

        accountRepository.save(account);

        String token = null;
        if (tokenNeedsRefresh) {
            token = jwtService.generateToken(account);
        }

        return UpdateProfileResponse.builder()
                .profile(getProfile(account))
                .token(token)
                .build();
    }

    @Transactional
    public void deleteProfile(Account account) {
        log.info("Suppression du profil de : {}", account.getEmail());
        
        List<Alert> userAlerts = alertRepository.findByAccount(account);
        if (!userAlerts.isEmpty()) {
            triggeredAlertRepository.deleteByAlertIn(userAlerts);
            alertRepository.deleteByAccount(account);
        }
        
        accountFavoriteAssetRepository.deleteByAccount(account);
        accountRepository.delete(account);
    }
}
