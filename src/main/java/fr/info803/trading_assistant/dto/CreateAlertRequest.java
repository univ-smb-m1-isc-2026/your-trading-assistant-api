package fr.info803.trading_assistant.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/*
    DTO pour la requête de création d'une alerte.

    Validations Jakarta :
    - symbol    : obligatoire (identifie l'actif à surveiller).
    - type      : obligatoire ("PRICE_THRESHOLD" ou "VOLUME_THRESHOLD").
    - direction : obligatoire ("ABOVE" ou "BELOW").
    - thresholdValue : obligatoire (le seuil numérique).
    - recurring : obligatoire (true = récurrente, false = one-shot).

    Le type et la direction sont transmis comme String et convertis en enum
    dans le service. Cela permet des messages d'erreur explicites en cas
    de valeur invalide (plutôt qu'une erreur de désérialisation Jackson).
*/
@Getter
@Setter
public class CreateAlertRequest {

    @NotBlank(message = "Le symbole de l'actif est obligatoire")
    private String symbol;

    @NotBlank(message = "Le type d'alerte est obligatoire")
    private String type;

    @NotBlank(message = "La direction est obligatoire")
    private String direction;

    @NotNull(message = "La valeur du seuil est obligatoire")
    private BigDecimal thresholdValue;

    @NotNull(message = "Le champ recurring est obligatoire")
    private Boolean recurring;
}
