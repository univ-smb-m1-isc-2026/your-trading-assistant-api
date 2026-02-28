package fr.info803.trading_assistant.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import fr.info803.trading_assistant.entity.Account;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

/*
    Service responsable de toutes les opérations liées aux tokens JWT.

    Un token JWT est composé de 3 parties séparées par des points :
      Header.Payload.Signature

    - Header : algorithme de signature (HS256)
    - Payload : les "claims" (données utiles : subject, expiration, claims custom)
    - Signature : HMAC-SHA256 du Header+Payload avec la clé secrète

    Exemple de payload décodé :
    {
      "sub": "user@email.com",   <- subject = identifiant
      "id": 1,
      "username": "Jean",
      "iat": 1700000000,         <- issued at
      "exp": 1700086400          <- expiration (iat + 24h)
    }
*/
@Slf4j
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    /*
        Génère un token JWT pour un compte utilisateur.
        Inclut dans le payload : id, username, email (via subject).
    */
    public String generateToken(Account account) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("id", account.getId());
        extraClaims.put("username", account.getDisplayUsername()); // nom d'affichage, pas l'email

        return Jwts.builder()
                .claims(extraClaims)
                .subject(account.getEmail())        // l'email est le subject (identifiant)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    /*
        Extrait l'email (subject) du token.
    */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /*
        Valide le token : vérifie que l'email correspond et que le token n'est pas expiré.
        Retourne false si l'email extrait est null (token malformé ou invalide).
    */
    public boolean isTokenValid(String token, Account account) {
        try {
            final String email = extractEmail(token);
            // Si email est null, le token est malformé/invalide, donc pas valide
            if (email == null) {
                return false;
            }
            return email.equals(account.getEmail()) && !isTokenExpired(token);
        } catch (JwtException e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    // --- Méthodes privées utilitaires ---

    boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /*
        Méthode générique pour extraire n'importe quel claim du token.
        Utilise une Function<Claims, T> pour extraire le claim souhaité.
    */
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /*
        Parse et valide la signature du token, retourne tous les claims.
        Lance une exception si la signature est invalide ou le token expiré.
    */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /*
        Convertit la clé secrète en SecretKey HMAC-SHA256.
        On utilise les bytes UTF-8 directement — la clé doit faire au moins 32 caractères
        pour garantir 256 bits de sécurité (requis pour HMAC-SHA256).
        En production, injectez une clé longue et aléatoire via la variable JWT_SECRET.
    */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
