package fr.info803.trading_assistant.exception;

/*
    Exception levée quand une requête de moyenne mobile est invalide.

    Cas d'usage :
      - Type de MA inconnu (ni "SMA" ni "EMA")
      - Liste de périodes vide ou absente
      - Période < 1

    Mappée en 400 Bad Request par GlobalExceptionHandler.
*/
public class InvalidMovingAverageRequestException extends RuntimeException {

    public InvalidMovingAverageRequestException(String message) {
        super(message);
    }
}
