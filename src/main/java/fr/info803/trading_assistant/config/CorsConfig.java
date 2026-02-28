package fr.info803.trading_assistant.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/*
    Configuration CORS (Cross-Origin Resource Sharing)

    CORS est un mécanisme de sécurité des navigateurs qui bloque les requêtes HTTP
    provenant d'un domaine différent de celui du serveur. Par exemple, si votre
    frontend tourne sur http://localhost:5173 et votre API sur http://localhost:8080,
    le navigateur bloque les requêtes par défaut.

    Ce bean déclare les origines autorisées à communiquer avec l'API.
    L'origine est lue depuis les fichiers application-{profil}.yaml,
    ce qui permet d'avoir une configuration différente selon l'environnement.
*/
@Configuration
public class CorsConfig {

    // Injecte la valeur de "cors.allowed-origins" depuis le fichier de propriétés
    // du profil actif (dev ou prod)
    @Value("${cors.allowed-origins}")
    private String allowedOrigin;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Origines autorisées (ex: http://localhost:5173 ou https://your-trading-assistant.oups.net)
        configuration.setAllowedOrigins(List.of(allowedOrigin));

        // Méthodes HTTP autorisées
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Headers autorisés dans les requêtes entrantes
        // Authorization est nécessaire pour transmettre les tokens JWT ou Bearer
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));

        // Autorise l'envoi de cookies et d'en-têtes d'authentification (ex: Bearer token)
        configuration.setAllowCredentials(true);

        // Durée (en secondes) pendant laquelle le navigateur peut mettre en cache
        // la réponse à une requête OPTIONS (preflight). Évite des allers-retours inutiles.
        configuration.setMaxAge(3600L);

        // Applique cette configuration à tous les endpoints de l'API
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
