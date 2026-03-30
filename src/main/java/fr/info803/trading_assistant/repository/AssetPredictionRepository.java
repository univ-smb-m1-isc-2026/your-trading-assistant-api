package fr.info803.trading_assistant.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetPrediction;
import fr.info803.trading_assistant.repository.projection.AssetBacktestResultProjection;
import fr.info803.trading_assistant.repository.projection.GlobalBacktestStatsProjection;

/**
 * Repository for managing AssetPrediction entities.
 */
@Repository
public interface AssetPredictionRepository
        extends JpaRepository<AssetPrediction, Long> {

    /**
     * Finds an asset prediction by asset and date.
     *
     * @param asset the asset
     * @param date  the date
     * @return an optional containing the prediction if found
     */
    Optional<AssetPrediction> findByAssetAndDate(Asset asset, LocalDate date);

    /**
     * Finds predictions for a given asset symbol ordered by date descending.
     *
     * @param symbol the asset symbol
     * @return list of predictions
     */
    List<AssetPrediction> findByAssetSymbolOrderByDateDesc(String symbol);

    /**
     * Finds top predictions by date ordered by absolute variation descending.
     *
     * @param date     the date
     * @param pageable pagination information
     * @return list of top predictions
     */
    @Query("SELECT p FROM AssetPrediction p WHERE p.date = :date "
            + "ORDER BY ABS(p.predictedVariation) DESC")
    List<AssetPrediction> findTopPredictionsByDateOrderByAbsoluteVariationDesc(
            @Param("date") LocalDate date,
            Pageable pageable
    );

    /**
     * Finds all predicted variations.
     *
     * @return list of all predicted variations
     */
    @Query("SELECT p.predictedVariation FROM AssetPrediction p "
            + "WHERE p.predictedVariation IS NOT NULL")
    List<BigDecimal> findAllPredictedVariations();

    /**
     * Gets backtest results grouped by asset within a date range.
     *
     * @param startDate the start date
     * @param endDate   the end date
     * @return list of asset backtest results
     */
    @Query(value = "SELECT a.symbol as symbol, "
            + "COUNT(p.id) as totalPredictions, "
            + "(SUM(CASE WHEN p.is_success = true THEN 1.0 ELSE 0.0 END) "
            + "* 100.0 / COUNT(p.id)) as successRatePct, "
            + "(SUM(CASE WHEN p.is_max_potential_success = true THEN 1.0 ELSE 0.0 END) "
            + "* 100.0 / COUNT(p.id)) as maxPotentialSuccessRatePct, "
            + "AVG(p.absolute_error) as meanAbsoluteErrorPct "
            + "FROM asset_prediction p "
            + "JOIN asset a ON p.asset_id = a.id "
            + "WHERE p.actual_variation_pct IS NOT NULL "
            + "AND p.prediction_date >= :startDate AND p.prediction_date <= :endDate "
            + "GROUP BY a.symbol "
            + "ORDER BY successRatePct DESC, meanAbsoluteErrorPct ASC",
            nativeQuery = true)
    List<AssetBacktestResultProjection> getAssetBacktestResults(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Gets backtest results for a specific asset within a date range.
     *
     * @param symbol    the asset symbol
     * @param startDate the start date
     * @param endDate   the end date
     * @return an optional asset backtest result projection
     */
    @Query(value = "SELECT a.symbol as symbol, "
            + "COUNT(p.id) as totalPredictions, "
            + "(SUM(CASE WHEN p.is_success = true THEN 1.0 ELSE 0.0 END) "
            + "* 100.0 / COUNT(p.id)) as successRatePct, "
            + "(SUM(CASE WHEN p.is_max_potential_success = true THEN 1.0 ELSE 0.0 END) "
            + "* 100.0 / COUNT(p.id)) as maxPotentialSuccessRatePct, "
            + "AVG(p.absolute_error) as meanAbsoluteErrorPct "
            + "FROM asset_prediction p "
            + "JOIN asset a ON p.asset_id = a.id "
            + "WHERE p.actual_variation_pct IS NOT NULL "
            + "AND p.prediction_date >= :startDate AND p.prediction_date <= :endDate "
            + "AND a.symbol = :symbol "
            + "GROUP BY a.symbol",
            nativeQuery = true)
    Optional<AssetBacktestResultProjection> getAssetBacktestResultBySymbol(
            @Param("symbol") String symbol,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Gets global backtest statistics within a date range.
     *
     * @param startDate the start date
     * @param endDate   the end date
     * @return global backtest stats projection
     */
    @Query(value = "SELECT COUNT(p.id) as totalPredictions, "
            + "(SUM(CASE WHEN p.is_success = true THEN 1.0 ELSE 0.0 END) "
            + "* 100.0 / COUNT(p.id)) as successRatePct, "
            + "(SUM(CASE WHEN p.is_max_potential_success = true THEN 1.0 ELSE 0.0 END) "
            + "* 100.0 / COUNT(p.id)) as maxPotentialSuccessRatePct, "
            + "AVG(p.absolute_error) as meanAbsoluteErrorPct "
            + "FROM asset_prediction p "
            + "WHERE p.actual_variation_pct IS NOT NULL "
            + "AND p.prediction_date >= :startDate AND p.prediction_date <= :endDate",
            nativeQuery = true)
    GlobalBacktestStatsProjection getGlobalBacktestStats(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
