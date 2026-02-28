package fr.info803.trading_assistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static org.springframework.security.config.Customizer.withDefaults;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/*
    Configuration de la sécurité

    Note : le CORS est activé via .cors(withDefaults()), ce qui demande à Spring Security
    de déléguer la configuration CORS au bean CorsConfigurationSource déclaré dans CorsConfig.
    Sans cet appel, Spring Security intercepterait les requêtes preflight (OPTIONS) avant
    que le filtre CORS ne puisse les traiter, ce qui provoquerait des erreurs 403.
*/
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(withDefaults()) // délègue à CorsConfig#corsConfigurationSource()
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/h2-console/**").permitAll() // autorise l'accès à la racine et à la console H2
                .anyRequest().authenticated() // toutes les autres requêtes nécessitent une authentification
            )
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .formLogin(withDefaults());
        return http.build();
    }
}
