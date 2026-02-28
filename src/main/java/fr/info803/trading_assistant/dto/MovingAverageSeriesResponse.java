package fr.info803.trading_assistant.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/*
    Une série complète de moyenne mobile pour un type et une période donnés.

    Champs :
      - type   : le type de MA ("SMA" ou "EMA")
      - period : la fenêtre de calcul (ex: 20 pour une SMA-20)
      - values : liste ordonnée (date ASC) des points {date, value}

    Exemple JSON :
      {
        "type": "SMA",
        "period": 20,
        "values": [
          { "date": "2026-01-15", "value": 95123.45 },
          { "date": "2026-01-16", "value": 95200.00 }
        ]
      }

    Pourquoi une série par type+période (et non un objet flat par date) ?
      → Permet au client de demander plusieurs périodes en un seul appel
        et de les itérer séparément pour le tracé sur un graphique.
*/
@Getter
@Builder
public class MovingAverageSeriesResponse {

    private String type;
    private int period;
    private List<MovingAveragePointResponse> values;
}
