package fr.info803.trading_assistant.exception;

/*
    Levée quand un utilisateur tente de modifier ou supprimer une alerte
    qui n'existe pas ou qui ne lui appartient pas.
    Traduite en HTTP 404 Not Found par GlobalExceptionHandler.

    Pourquoi stocker l'alertId plutôt que le symbol ?
    - Les alertes sont identifiées par leur ID (pas par un symbol).
    - Un utilisateur peut avoir plusieurs alertes sur le même symbol.
    - L'ID est le seul identifiant unique pour une alerte.
*/
public class AlertNotFoundException extends RuntimeException {

    private final Long alertId;

    public AlertNotFoundException(Long alertId) {
        super("Alert not found: " + alertId);
        this.alertId = alertId;
    }

    public Long getAlertId() {
        return alertId;
    }
}
