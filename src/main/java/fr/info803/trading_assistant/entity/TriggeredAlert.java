package fr.info803.trading_assistant.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
    Représente un déclenchement d'alerte : enregistre le fait qu'une
    condition configurée dans Alert a été satisfaite pour une bougie donnée.

    Table unique pour TOUS les types d'alertes (prix, volume, futurs...).
    Le contexte complet (type, direction, seuil, symbol) est reconstruit
    par jointure vers Alert lors de la construction de la réponse API.

    Pourquoi une table unique plutôt qu'une table par type ?
    - Tous les types partagent la même structure de déclenchement :
      "quelle alerte, quelle date, quelle valeur a déclenché, quand détecté".
    - Une seule requête SQL pour "toutes mes alertes déclenchées" (pas d'UNION).
    - Ajouter un nouveau type d'alerte ne nécessite aucune migration SQL.
    - Si un futur type a besoin de données supplémentaires, on pourra ajouter
      un champ JSON "metadata" sans impacter les types existants.

    triggeredValue (BigDecimal) :
    - La valeur réelle qui a satisfait la condition.
    - Pour PRICE_THRESHOLD + ABOVE : candle.high qui a dépassé le seuil.
    - Pour PRICE_THRESHOLD + BELOW : candle.low qui est passé sous le seuil.
    - Pour VOLUME_THRESHOLD : candle.volume qui a franchi le seuil.
    - Permet à l'utilisateur de voir "pourquoi" l'alerte s'est déclenchée.

    Contrainte UNIQUE (alert_id, candle_date) :
    - Empêche de déclencher la même alerte deux fois pour la même journée.
    - Protection contre les re-runs du scheduler ou les relances manuelles.
*/
@Entity
@Table(
    name = "triggered_alert",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_triggered_alert_alert_candle_date",
        columnNames = {"alert_id", "candle_date"}
    )
)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TriggeredAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "triggered_alert_seq")
    @SequenceGenerator(name = "triggered_alert_seq", sequenceName = "triggered_alert_sequence", allocationSize = 1)
    private Long id;

    /*
        Lien vers la configuration d'alerte qui a été satisfaite.
        FetchType.LAZY : on ne charge pas l'alerte tant qu'on n'en a pas besoin.
        En pratique, le service fait un JOIN FETCH ou accède explicitement
        pour construire la réponse API.
    */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id", nullable = false)
    private Alert alert;

    // La valeur réelle qui a satisfait la condition (prix high/low, volume...).
    @Column(nullable = false, precision = 30, scale = 10)
    private BigDecimal triggeredValue;

    // La date de la bougie qui a causé le déclenchement.
    @Column(name = "candle_date", nullable = false)
    private LocalDate candleDate;

    // Horodatage du moment où le déclenchement a été détecté par le scheduler.
    @Column(nullable = false)
    private LocalDateTime triggeredAt;
}
