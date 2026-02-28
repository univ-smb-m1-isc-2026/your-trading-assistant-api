package fr.info803.trading_assistant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

/*
    Un point de données dans une série de moyenne mobile.

    Chaque point contient :
      - date  : le jour pour lequel la MA est calculée
      - value : la valeur de la MA à cette date (BigDecimal pour la précision financière)

    Exemple JSON :
      { "date": "2026-02-27", "value": 95123.45 }
*/
@Getter
@Builder
public class MovingAveragePointResponse {

    private LocalDate date;
    private BigDecimal value;
}
