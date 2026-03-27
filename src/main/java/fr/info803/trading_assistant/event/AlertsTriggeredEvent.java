package fr.info803.trading_assistant.event;

import java.time.LocalDate;
import java.util.List;
import fr.info803.trading_assistant.entity.TriggeredAlert;

/**
 * Événement déclenché à la fin d'un cycle de synchronisation
 * contenant toutes les alertes déclenchées.
 */
public record AlertsTriggeredEvent(
    List<TriggeredAlert> triggeredAlerts,
    LocalDate date
) {}
