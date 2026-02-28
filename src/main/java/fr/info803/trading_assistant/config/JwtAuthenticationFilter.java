package fr.info803.trading_assistant.config;

import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import fr.info803.trading_assistant.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
    Filtre JWT exécuté une seule fois par requête HTTP (OncePerRequestFilter).

    Utilise UserDetailsService (bean déclaré dans ApplicationConfig) plutôt qu'AccountService
    directement, afin d'éviter toute dépendance circulaire au démarrage du contexte Spring.

    Flux d'exécution pour chaque requête :
      1. Lit l'en-tête "Authorization: Bearer <token>"
      2. Extrait l'email depuis le token via JwtService
      3. Charge l'Account depuis la base via UserDetailsService
      4. Valide le token (signature + expiration + email)
      5. Si valide : injecte l'Account dans le SecurityContext
      6. Passe la requête au filtre suivant (et au contrôleur)

    Si l'en-tête est absent ou le token invalide, la requête continue sans authentification.
    Spring Security bloquera alors l'accès aux routes protégées avec un 401 ou 403.
*/
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Si l'en-tête est absent ou ne commence pas par "Bearer ", on passe au filtre suivant
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extrait le token (tout ce qui suit "Bearer ")
        final String jwt = authHeader.substring(7);
        final String email = jwtService.extractEmail(jwt);

        // Si l'email est présent et qu'aucune authentification n'est déjà en place dans le contexte
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                if (jwtService.isTokenValid(jwt, (fr.info803.trading_assistant.entity.Account) userDetails)) {
                    /*
                        Crée un token d'authentification Spring Security et l'injecte dans le SecurityContext.
                        À partir de ce moment, la requête est considérée comme authentifiée.
                        Les contrôleurs peuvent accéder à l'utilisateur via SecurityContextHolder.
                    */
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (UsernameNotFoundException e) {
                // L'utilisateur référencé par le token n'existe pas en base (ex : base H2 réinitialisée,
                // compte supprimé, token périmé d'une session précédente).
                // On continue la chaîne sans authentifier : Spring Security gérera l'accès selon la route.
                log.warn("Token JWT reçu pour un utilisateur inconnu : {}", email);
            }
        }

        filterChain.doFilter(request, response);
    }
}
