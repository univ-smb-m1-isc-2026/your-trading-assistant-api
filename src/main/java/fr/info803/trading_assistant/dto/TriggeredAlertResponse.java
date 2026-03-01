package fr.info803.trading_assistant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/*
    DTO de réponse pour un déclenchement d'alerte.

    Retourné par GET /alerts/triggered.
    Contient le contexte complet du déclenchement pour que le client
    puisse afficher un historique détaillé sans requêtes supplémentaires.

    Informations de la configuration (dénormalisées depuis Alert) :
    - alertId, symbol, type, direction, thresholdValue

    Informations du déclenchement (depuis TriggeredAlert) :
    - id, triggeredValue, candleDate, triggeredAt
*/
@Getter
@Builder
public class TriggeredAlertResponse {

    private Long id;
    private Long alertId;
    private String symbol;
    private String type;
    private String direction;
    private BigDecimal thresholdValue;
    private BigDecimal triggeredValue;
    private LocalDate candleDate;
    private LocalDateTime triggeredAt;
}
