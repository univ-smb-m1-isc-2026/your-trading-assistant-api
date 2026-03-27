package fr.info803.trading_assistant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

/*
    DTO de réponse pour une alerte configurée.

    Retourné par GET /alerts et PUT /alerts/{id}.
    Contient toutes les informations de configuration de l'alerte
    pour que le client puisse les afficher et les modifier.

    Le symbol est dénormalisé depuis Alert.asset.symbol pour éviter
    au client de faire une requête supplémentaire pour le résoudre.
*/
@Getter
@Builder
public class AlertResponse {

    private Long id;
    private String symbol;
    private String type;
    private String direction;
    private BigDecimal thresholdValue;
    private boolean recurring;
    private boolean active;
    private LocalDateTime createdAt;
    // Champs spécifiques à MA_CROSSOVER (null pour les autres types)
    private Integer shortPeriod;
    private Integer longPeriod;
    private String maType;
}
