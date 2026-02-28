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
