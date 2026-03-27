package fr.info803.trading_assistant.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/*
    DTO pour la requête de modification d'une alerte existante.

    Tous les champs sont optionnels (nullable) : seuls les champs
    fournis dans le JSON seront mis à jour. Les champs absents ou null
    ne modifient pas la valeur existante en base.

    Cela permet des mises à jour partielles :
    - Modifier seulement le seuil : { "thresholdValue": 105000 }
    - Réactiver une alerte one-shot : { "active": true }
    - Changer la direction : { "direction": "BELOW" }

    Le type et la direction sont transmis comme String (même logique
    que CreateAlertRequest).
*/
@Getter
@Setter
public class UpdateAlertRequest {

    private String type;
    private String direction;
    private BigDecimal thresholdValue;
    private Boolean recurring;
    private Boolean active;
    private Integer shortPeriod;
    private Integer longPeriod;
    private String maType;
}
