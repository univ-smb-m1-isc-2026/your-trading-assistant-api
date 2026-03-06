package fr.info803.trading_assistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import static org.springframework.security.config.Customizer.withDefaults;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;

/*
    Configuration principale de Spring Security.

    Note CORS : .cors(withDefaults()) délègue à CorsConfig#corsConfigurationSource().
    Sans cet appel, Spring Security intercepterait les requêtes preflight (OPTIONS) avant
    que le filtre CORS ne puisse les traiter, ce qui provoquerait des erreurs 403.

    Changements pour JWT :
      - CSRF désactivé : inutile en mode stateless (le JWT remplace le cookie de session)
      - Session STATELESS : Spring Security ne crée plus de session HTTP côté serveur
      - formLogin supprimé : remplacé par le flux register/login JWT
      - /auth/** en accès public : pour que register et login soient accessibles sans token
      - JwtAuthenticationFilter ajouté AVANT UsernamePasswordAuthenticationFilter :
        il intercepte les requêtes avec un Bearer token et authentifie l'utilisateur
        avant que Spring Security ne tente son propre mécanisme d'authentification
*/
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(withDefaults())
            .csrf(csrf -> csrf.disable()) // désactivé : JWT est stateless, pas de session cookie
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // pas de session HTTP côté serveur
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()             // register et login sont publics
                .requestMatchers("/").permitAll()                    // route racine publique
                .requestMatchers("/actuator/**").permitAll()         // monitoring public pour Prometheus
                .requestMatchers("/dev/**").permitAll()              // outils de diagnostic dev (bean absent en prod)
                .anyRequest().authenticated()                        // tout le reste requiert un JWT valide
            )
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
