package fr.info803.trading_assistant.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.info803.trading_assistant.client.AiPredictionClient;
import fr.info803.trading_assistant.dto.AiPredictionResponse;
import fr.info803.trading_assistant.dto.AssetBacktestResultDto;
import fr.info803.trading_assistant.dto.GlobalBacktestStatsDto;
import fr.info803.trading_assistant.dto.PredictionFeaturesDto;
import fr.info803.trading_assistant.dto.PredictionStatsDto;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetDailyValue;
import fr.info803.trading_assistant.entity.AssetPrediction;
import fr.info803.trading_assistant.repository.AssetDailyValueRepository;
import fr.info803.trading_assistant.repository.AssetPredictionRepository;
import fr.info803.trading_assistant.repository.AssetRepository;
import fr.info803.trading_assistant.repository.projection.GlobalBacktestStatsProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for generating and evaluating AI predictions for assets.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetPredictionService {

    /** The required number of historical candles to compute features. */
    private static final int MIN_REQUIRED_CANDLES = 50;

    /** Scale for decimal division. */
    private static final int DECIMAL_SCALE = 6;

    /** Scale for precise percentage calculation. */
    private static final int PCT_SCALE = 10;

    /** Multiplier to convert to percentage. */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** The divisor 2. */
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    /** Repository for asset daily values. */
    private final AssetDailyValueRepository assetDailyValueRepository;

    /** Repository for assets. */
    private final AssetRepository assetRepository;

    /** Service for prediction features. */
    private final PredictionFeatureService predictionFeatureService;

    /** Client for external AI API. */
    private final AiPredictionClient aiPredictionClient;

    /** Repository for asset predictions. */
    private final AssetPredictionRepository assetPredictionRepository;

    /**
     * Generates predictions for all available assets for the given date.
     *
     * @param date The date for which to generate predictions
     */
    public void generatePredictionsForDate(final LocalDate date) {
        log.info("Starting AI prediction generation for all assets on date {}",
                date);
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
    public void generateAndSavePrediction(final Asset asset,
                                          final LocalDate date) {
        log.info("Generating AI prediction for asset {} on date {}",
                asset.getSymbol(), date);

        List<AssetDailyValue> recentCandlesDesc = assetDailyValueRepository
                .findTop60ByAssetAndDateLessThanEqualOrderByDateDesc(
                        asset, date);

        if (recentCandlesDesc.size() < MIN_REQUIRED_CANDLES) {
            log.warn("Not enough historical data to compute AI prediction for "
                            + "{} on {}. Required: {}, Found: {}",
                    asset.getSymbol(), date, MIN_REQUIRED_CANDLES,
                    recentCandlesDesc.size());
            return;
        }

        // Feature service expects chronological order (oldest to newest)
        List<AssetDailyValue> recentCandlesAsc = recentCandlesDesc.stream()
                .sorted(Comparator.comparing(AssetDailyValue::getDate))
                .toList();

        try {
            // Calculate features
            PredictionFeaturesDto features = predictionFeatureService
                    .calculateForLatestCandle(recentCandlesAsc);

            // Fetch prediction from external AI service
            AiPredictionResponse predictionResponse = aiPredictionClient
                    .predict(features);

            // Upsert into database
            AssetPrediction prediction = assetPredictionRepository
                    .findByAssetAndDate(asset, date)
                    .orElseGet(() -> AssetPrediction.builder()
                            .asset(asset)
                            .date(date)
                            .build());

            prediction.setPredictedVariation(
                    predictionResponse.predictedVariationPct());
            assetPredictionRepository.save(prediction);

            log.info("Successfully generated prediction for {}: {}%",
                    asset.getSymbol(),
                    predictionResponse.predictedVariationPct());

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
        List<BigDecimal> variations = assetPredictionRepository
                .findAllPredictedVariations();

        if (variations == null || variations.isEmpty()) {
            return new PredictionStatsDto(BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0L);
        }

        List<BigDecimal> sortedVariations = new ArrayList<>(variations);
        Collections.sort(sortedVariations);
        int size = sortedVariations.size();

        BigDecimal min = sortedVariations.get(0);
        BigDecimal max = sortedVariations.get(size - 1);

        BigDecimal sum = sortedVariations.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = sum.divide(
                BigDecimal.valueOf(size), DECIMAL_SCALE,
                RoundingMode.HALF_UP);

        BigDecimal median;
        if (size % 2 == 0) {
            BigDecimal mid1 = sortedVariations.get(size / 2 - 1);
            BigDecimal mid2 = sortedVariations.get(size / 2);
            median = mid1.add(mid2).divide(TWO, DECIMAL_SCALE,
                    RoundingMode.HALF_UP);
        } else {
            median = sortedVariations.get(size / 2);
        }

        return new PredictionStatsDto(min, max, mean, median, size);
    }

    /**
     * Evaluates pending predictions for a target date to backtest performance.
     *
     * @param targetDate The date to evaluate pending predictions.
     */
    @Transactional
    public void evaluatePendingPredictions(final LocalDate targetDate) {
        log.info("Evaluating pending AI predictions for date {}", targetDate);
        LocalDate predictionDate = targetDate.minusDays(1);

        List<Asset> assets = assetRepository.findAll();
        for (Asset asset : assets) {
            Optional<AssetPrediction> predictionOpt = assetPredictionRepository
                    .findByAssetAndDate(asset, predictionDate);
            if (predictionOpt.isEmpty()) {
                continue;
            }
            AssetPrediction prediction = predictionOpt.get();

            // Ignore if already evaluated
            if (prediction.getActualVariation() != null) {
                continue;
            }

            Optional<AssetDailyValue> candleTOpt = assetDailyValueRepository
                    .findByAssetAndDate(asset, targetDate);
            Optional<AssetDailyValue> candleTMinus1Opt =
                    assetDailyValueRepository
                            .findByAssetAndDate(asset, predictionDate);

            if (candleTOpt.isEmpty() || candleTMinus1Opt.isEmpty()) {
                continue;
            }

            AssetDailyValue candleT = candleTOpt.get();
            AssetDailyValue candleTMinus1 = candleTMinus1Opt.get();

            BigDecimal closeTMinus1 = candleTMinus1.getClose();
            if (closeTMinus1.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            BigDecimal predictedVar = prediction.getPredictedVariation();

            // 1. Classic Close-to-Close calculation
            BigDecimal actualVar = candleT.getClose().subtract(closeTMinus1)
                    .divide(closeTMinus1, PCT_SCALE, RoundingMode.HALF_UP)
                    .multiply(HUNDRED);

            // 2. Max Potential calculation (High if predicted UP, Low if predicted DOWN)
            BigDecimal maxPotentialPriceT;
            if (predictedVar.compareTo(BigDecimal.ZERO) > 0) {
                maxPotentialPriceT = candleT.getHigh();
            } else if (predictedVar.compareTo(BigDecimal.ZERO) < 0) {
                maxPotentialPriceT = candleT.getLow();
            } else {
                maxPotentialPriceT = candleT.getClose();
            }

            BigDecimal maxPotentialVar = maxPotentialPriceT.subtract(closeTMinus1)
                    .divide(closeTMinus1, PCT_SCALE, RoundingMode.HALF_UP)
                    .multiply(HUNDRED);

            // Direction success (Close to Close)
            boolean isSuccess = false;
            if (predictedVar.compareTo(BigDecimal.ZERO) > 0
                    && actualVar.compareTo(BigDecimal.ZERO) > 0) {
                isSuccess = true;
            } else if (predictedVar.compareTo(BigDecimal.ZERO) < 0
                    && actualVar.compareTo(BigDecimal.ZERO) < 0) {
                isSuccess = true;
            } else if (predictedVar.compareTo(BigDecimal.ZERO) == 0
                    && actualVar.compareTo(BigDecimal.ZERO) == 0) {
                isSuccess = true;
            }

            // Direction success (Max Potential)
            boolean isMaxPotentialSuccess = false;
            if (predictedVar.compareTo(BigDecimal.ZERO) > 0
                    && maxPotentialVar.compareTo(BigDecimal.ZERO) > 0) {
                isMaxPotentialSuccess = true;
            } else if (predictedVar.compareTo(BigDecimal.ZERO) < 0
                    && maxPotentialVar.compareTo(BigDecimal.ZERO) < 0) {
                isMaxPotentialSuccess = true;
            } else if (predictedVar.compareTo(BigDecimal.ZERO) == 0
                    && maxPotentialVar.compareTo(BigDecimal.ZERO) == 0) {
                isMaxPotentialSuccess = true;
            }

            // Absolute error (always based on Close to Close, which is the real outcome)
            BigDecimal absoluteError = predictedVar.subtract(actualVar).abs();

            prediction.setActualVariation(actualVar);
            prediction.setMaxPotentialVariation(maxPotentialVar);
            prediction.setIsSuccess(isSuccess);
            prediction.setIsMaxPotentialSuccess(isMaxPotentialSuccess);
            prediction.setAbsoluteError(absoluteError);

            assetPredictionRepository.save(prediction);
            log.debug("Evaluated prediction for {} on {}: "
                            + "predicted={}, actual={}, maxPotential={}, success={}",
                    asset.getSymbol(), predictionDate, predictedVar,
                    actualVar, maxPotentialVar, isSuccess);
        }
    }

    /**
     * Gets the backtest results for a specific asset, optionally filtered.
     *
     * @param symbol    The asset symbol.
     * @param startDate The optional start date.
     * @param endDate   The optional end date.
     * @return The asset backtest result.
     */
    public AssetBacktestResultDto getAssetBacktestResult(
            final String symbol,
            final LocalDate startDate,
            final LocalDate endDate) {

        final LocalDate start = startDate != null ? startDate : LocalDate.of(2000, 1, 1);
        final LocalDate end = endDate != null ? endDate : LocalDate.of(2099, 12, 31);

        return assetPredictionRepository.getAssetBacktestResultBySymbol(
                        symbol.toUpperCase(), start, end)
                .map(p -> new AssetBacktestResultDto(
                        p.getSymbol(),
                        p.getTotalPredictions(),
                        p.getSuccessRatePct(),
                        p.getMaxPotentialSuccessRatePct(),
                        p.getMeanAbsoluteErrorPct()
                ))
                .orElseGet(() -> new AssetBacktestResultDto(
                        symbol.toUpperCase(),
                        0L,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                ));
    }

    /**
     * Gets the backtest results grouped by asset, optionally filtered by date.
     *
     * @param startDate The optional start date.
     * @param endDate   The optional end date.
     * @return A list of asset backtest results.
     */
    public List<AssetBacktestResultDto> getAssetBacktestResults(
            final LocalDate startDate,
            final LocalDate endDate) {

        final LocalDate start = startDate != null ? startDate : LocalDate.of(2000, 1, 1);
        final LocalDate end = endDate != null ? endDate : LocalDate.of(2099, 12, 31);

        return assetPredictionRepository.getAssetBacktestResults(start, end)
                .stream()
                .map(p -> new AssetBacktestResultDto(
                        p.getSymbol(),
                        p.getTotalPredictions(),
                        p.getSuccessRatePct(),
                        p.getMaxPotentialSuccessRatePct(),
                        p.getMeanAbsoluteErrorPct()
                )).toList();
    }

    /**
     * Gets the global backtest statistics, optionally filtered by date.
     *
     * @param startDate The optional start date.
     * @param endDate   The optional end date.
     * @return The global backtest statistics.
     */
    public GlobalBacktestStatsDto getGlobalBacktestStats(
            final LocalDate startDate,
            final LocalDate endDate) {

        final LocalDate start = startDate != null ? startDate : LocalDate.of(2000, 1, 1);
        final LocalDate end = endDate != null ? endDate : LocalDate.of(2099, 12, 31);

        GlobalBacktestStatsProjection p = assetPredictionRepository
                .getGlobalBacktestStats(start, end);

        if (p == null || p.getTotalPredictions() == 0) {
            return new GlobalBacktestStatsDto(0L, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO);
        }

        return new GlobalBacktestStatsDto(
                p.getTotalPredictions(),
                p.getSuccessRatePct(),
                p.getMaxPotentialSuccessRatePct(),
                p.getMeanAbsoluteErrorPct()
        );
    }
}
