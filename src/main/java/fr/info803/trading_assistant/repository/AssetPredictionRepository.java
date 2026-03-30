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

@Repository
public interface AssetPredictionRepository extends JpaRepository<AssetPrediction, Long> {

    Optional<AssetPrediction> findByAssetAndDate(Asset asset, LocalDate date);

    List<AssetPrediction> findByAssetSymbolOrderByDateDesc(String symbol);

    @Query("SELECT p FROM AssetPrediction p WHERE p.date = :date ORDER BY ABS(p.predictedVariation) DESC")
    List<AssetPrediction> findTopPredictionsByDateOrderByAbsoluteVariationDesc(
            @Param("date") LocalDate date, 
            Pageable pageable
    );

    @Query("SELECT p.predictedVariation FROM AssetPrediction p WHERE p.predictedVariation IS NOT NULL")
    List<BigDecimal> findAllPredictedVariations();
}
