package fr.info803.trading_assistant.event;

import fr.info803.trading_assistant.entity.Alert;

/**
 * Événement déclenché lors de la création d'une alerte.
 */
public record AlertCreatedEvent(
    Alert alert,
    String accountEmail
) {}
