package fr.info803.trading_assistant.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.info803.trading_assistant.client.AiPredictionClient;
import fr.info803.trading_assistant.dto.AiPredictionResponse;
import fr.info803.trading_assistant.dto.PredictionFeaturesDto;
import fr.info803.trading_assistant.dto.PredictionStatsDto;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetDailyValue;
import fr.info803.trading_assistant.entity.AssetPrediction;
import fr.info803.trading_assistant.repository.AssetDailyValueRepository;
import fr.info803.trading_assistant.repository.AssetPredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssetPredictionService {

    private final AssetDailyValueRepository assetDailyValueRepository;
    private final fr.info803.trading_assistant.repository.AssetRepository assetRepository;
    private final PredictionFeatureService predictionFeatureService;
    private final AiPredictionClient aiPredictionClient;
    private final AssetPredictionRepository assetPredictionRepository;

    /**
     * Generates predictions for all available assets for the given date.
     * 
     * @param date The date for which to generate predictions
     */
    public void generatePredictionsForDate(LocalDate date) {
        log.info("Starting AI prediction generation for all assets on date {}", date);
        List<Asset> assets = assetRepository.findAll();
        
        for (Asset asset : assets) {
            generateAndSavePrediction(asset, date);
        }
        log.info("Finished AI prediction generation for date {}", date);
    }

    /**
     * Orchestrates the prediction flow:
     * 1. Fetches historical candles (up to 60 days back).
     * 2. Calculates technical features.
     * 3. Sends features to the AI API.
     * 4. Upserts the prediction into the database.
     *
     * @param asset The asset for which to generate a prediction.
     * @param date  The date of the latest candle.
     */
    @Transactional
    public void generateAndSavePrediction(Asset asset, LocalDate date) {
        log.info("Generating AI prediction for asset {} on date {}", asset.getSymbol(), date);

        List<AssetDailyValue> recentCandlesDesc = assetDailyValueRepository
                .findTop60ByAssetAndDateLessThanEqualOrderByDateDesc(asset, date);

        if (recentCandlesDesc.size() < 50) {
            log.warn("Not enough historical data to compute AI prediction for {} on {}. Required: 50, Found: {}", 
                     asset.getSymbol(), date, recentCandlesDesc.size());
            return;
        }

        // Feature service expects chronological order (oldest to newest)
        List<AssetDailyValue> recentCandlesAsc = recentCandlesDesc.stream()
                .sorted(Comparator.comparing(AssetDailyValue::getDate))
                .toList();

        try {
            // Calculate features
            PredictionFeaturesDto features = predictionFeatureService.calculateForLatestCandle(recentCandlesAsc);

            // Fetch prediction from external AI service
            AiPredictionResponse predictionResponse = aiPredictionClient.predict(features);

            // Upsert into database
            AssetPrediction prediction = assetPredictionRepository.findByAssetAndDate(asset, date)
                    .orElseGet(() -> AssetPrediction.builder()
                            .asset(asset)
                            .date(date)
                            .build());

            prediction.setPredictedVariation(predictionResponse.predictedVariationPct());
            assetPredictionRepository.save(prediction);

            log.info("Successfully generated prediction for {}: {}%", 
                     asset.getSymbol(), predictionResponse.predictedVariationPct());

        } catch (Exception e) {
            log.error("Failed to generate/save prediction for {} on {}: {}", 
                      asset.getSymbol(), date, e.getMessage(), e);
        }
    }

    /**
     * Calculates global statistics for all predictions in the database.
     * 
     * @return PredictionStatsDto containing min, max, mean, median, and count.
     */
    public PredictionStatsDto getGlobalPredictionStats() {
        List<BigDecimal> variations = assetPredictionRepository.findAllPredictedVariations();
        
        if (variations == null || variations.isEmpty()) {
            return new PredictionStatsDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L);
        }

        List<BigDecimal> sortedVariations = new ArrayList<>(variations);
        Collections.sort(sortedVariations);
        int size = sortedVariations.size();

        BigDecimal min = sortedVariations.get(0);
        BigDecimal max = sortedVariations.get(size - 1);

        BigDecimal sum = sortedVariations.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = sum.divide(BigDecimal.valueOf(size), 6, RoundingMode.HALF_UP);

        BigDecimal median;
        if (size % 2 == 0) {
            BigDecimal mid1 = sortedVariations.get(size / 2 - 1);
            BigDecimal mid2 = sortedVariations.get(size / 2);
            median = mid1.add(mid2).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
        } else {
            median = sortedVariations.get(size / 2);
        }

        return new PredictionStatsDto(min, max, mean, median, size);
    }
}
