package fr.info803.trading_assistant.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

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

/**
 * Entity representing an AI prediction for the next day's asset variation.
 */
@Entity
@Table(
    name = "asset_prediction",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_asset_prediction_date",
            columnNames = {"asset_id", "prediction_date"}
        )
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "asset_prediction_seq")
    @SequenceGenerator(
        name = "asset_prediction_seq",
        sequenceName = "asset_prediction_sequence",
        allocationSize = 1
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "prediction_date", nullable = false)
    private LocalDate date;

    @Column(name = "predicted_variation_pct", nullable = false, precision = 19, scale = 4)
    private BigDecimal predictedVariation;
}
