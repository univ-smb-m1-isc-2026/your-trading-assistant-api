package fr.info803.trading_assistant.exception;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/*
    Gestionnaire global des exceptions pour les endpoints asset.

    @ControllerAdvice intercepte les exceptions levées dans tous les @RestController.

    Exemple de réponse 404 pour AssetNotFoundException :
      {
        "error": "Asset not found",
        "symbol": "UNKNOWN",
        "timestamp": "2025-01-15T12:34:56"
      }
*/
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AssetNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAssetNotFound(AssetNotFoundException ex) {
        ErrorResponse error = ErrorResponse.builder()
            .error("Asset not found")
            .symbol(ex.getSymbol())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /*
        409 Conflict : l'asset est déjà dans les favoris de l'utilisateur.
        On renvoie le même format ErrorResponse pour cohérence côté client.
    */
    @ExceptionHandler(FavoriteAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleFavoriteAlreadyExists(FavoriteAlreadyExistsException ex) {
        ErrorResponse error = ErrorResponse.builder()
            .error("Asset already in favorites")
            .symbol(ex.getSymbol())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /*
        404 Not Found : l'asset n'est pas dans les favoris de l'utilisateur.
        Distinction avec AssetNotFoundException (asset inexistant en base) :
        ici, l'asset existe bien, mais il n'est pas en favori pour cet utilisateur.
    */
    @ExceptionHandler(FavoriteNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFavoriteNotFound(FavoriteNotFoundException ex) {
        ErrorResponse error = ErrorResponse.builder()
            .error("Asset not in favorites")
            .symbol(ex.getSymbol())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /*
        404 Not Found : l'alerte n'existe pas ou n'appartient pas à l'utilisateur.
        Utilise un format dédié AlertErrorResponse car l'identifiant est un ID
        numérique (Long) et non un symbol (String).
    */
    @ExceptionHandler(AlertNotFoundException.class)
    public ResponseEntity<AlertErrorResponse> handleAlertNotFound(AlertNotFoundException ex) {
        AlertErrorResponse error = AlertErrorResponse.builder()
            .error("Alert not found")
            .alertId(ex.getAlertId())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /*
        400 Bad Request : paramètres de requête de moyenne mobile invalides.
        Type de MA inconnu, période < 1, liste de périodes vide, etc.
        Utilise un format de réponse dédié (message au lieu de symbol)
        car l'erreur porte sur les paramètres, pas sur un asset.
    */
    @ExceptionHandler(InvalidMovingAverageRequestException.class)
    public ResponseEntity<ValidationErrorResponse> handleInvalidMovingAverageRequest(
            InvalidMovingAverageRequestException ex) {
        ValidationErrorResponse error = ValidationErrorResponse.builder()
            .error("Invalid moving average request")
            .message(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // ───────────────────────────────────────────────────────────────────────

    record AlertErrorResponse(String error, Long alertId, LocalDateTime timestamp) {
        static class Builder {
            private String error;
            private Long alertId;
            private LocalDateTime timestamp;

            public Builder error(String error) {
                this.error = error;
                return this;
            }

            public Builder alertId(Long alertId) {
                this.alertId = alertId;
                return this;
            }

            public Builder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public AlertErrorResponse build() {
                return new AlertErrorResponse(error, alertId, timestamp);
            }
        }

        public static Builder builder() {
            return new Builder();
        }
    }

    // ───────────────────────────────────────────────────────────────────────

    record ValidationErrorResponse(String error, String message, LocalDateTime timestamp) {
        static class Builder {
            private String error;
            private String message;
            private LocalDateTime timestamp;

            public Builder error(String error) {
                this.error = error;
                return this;
            }

            public Builder message(String message) {
                this.message = message;
                return this;
            }

            public Builder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public ValidationErrorResponse build() {
                return new ValidationErrorResponse(error, message, timestamp);
            }
        }

        public static Builder builder() {
            return new Builder();
        }
    }

    // ───────────────────────────────────────────────────────────────────────

    record ErrorResponse(String error, String symbol, LocalDateTime timestamp) {
        static class Builder {
            private String error;
            private String symbol;
            private LocalDateTime timestamp;

            public Builder error(String error) {
                this.error = error;
                return this;
            }

            public Builder symbol(String symbol) {
                this.symbol = symbol;
                return this;
            }

            public Builder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public ErrorResponse build() {
                return new ErrorResponse(error, symbol, timestamp);
            }
        }

        public static Builder builder() {
            return new Builder();
        }
    }
}
