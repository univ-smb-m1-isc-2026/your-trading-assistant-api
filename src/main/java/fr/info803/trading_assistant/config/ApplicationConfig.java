package fr.info803.trading_assistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import fr.info803.trading_assistant.repository.AccountRepository;
import lombok.RequiredArgsConstructor;

/*
    Configuration des beans Spring Security fondamentaux.

    Séparée de SecurityConfig pour éviter une dépendance circulaire :
      AccountService → AuthenticationManager → UserDetailsService → AccountService

    En isolant UserDetailsService ici (via lambda + AccountRepository directement),
    AccountService peut injecter AuthenticationManager sans créer de cycle.

    Chaîne de dépendances sans cycle :
      ApplicationConfig → AccountRepository (pas de cycle)
      AccountService    → AccountRepository, PasswordEncoder, JwtService, AuthenticationManager
      SecurityConfig    → JwtAuthenticationFilter, AuthenticationProvider
*/
@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    private final AccountRepository accountRepository;

    /*
        Charge un utilisateur depuis la base de données via son email.
        Utilisé par DaoAuthenticationProvider lors de l'authentification.
        On le déclare ici (et non dans AccountService) pour éviter le cycle.
    */
    @Bean
    public UserDetailsService userDetailsService() {
        return email -> accountRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Compte introuvable : " + email));
    }

    /*
        BCryptPasswordEncoder : algorithme de hachage fort pour les mots de passe.
        BCrypt intègre automatiquement un "salt" aléatoire, ce qui rend chaque
        hash unique même pour des mots de passe identiques.
    */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /*
        DaoAuthenticationProvider : connecte Spring Security à notre base de données.
        Lors d'une authentification, il :
          1. Charge l'utilisateur via userDetailsService() (passé dans le constructeur)
          2. Compare le mot de passe fourni avec le hash BCrypt via passwordEncoder()
          3. Lance BadCredentialsException si invalide

        Note : Spring Security 7 (Spring Boot 4) a supprimé le constructeur sans argument.
        UserDetailsService doit maintenant être passé dans le constructeur.
    */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /*
        Expose l'AuthenticationManager comme Bean Spring pour pouvoir l'injecter
        dans AccountService. AuthenticationConfiguration le construit lazily
        sans créer de dépendance circulaire.
    */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
