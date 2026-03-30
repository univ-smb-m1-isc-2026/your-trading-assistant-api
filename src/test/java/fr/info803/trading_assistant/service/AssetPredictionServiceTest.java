package fr.info803.trading_assistant.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fr.info803.trading_assistant.client.AiPredictionClient;
import fr.info803.trading_assistant.dto.AiPredictionResponse;
import fr.info803.trading_assistant.dto.PredictionFeaturesDto;
import fr.info803.trading_assistant.dto.PredictionStatsDto;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetDailyValue;
import fr.info803.trading_assistant.entity.AssetPrediction;
import fr.info803.trading_assistant.repository.AssetDailyValueRepository;
import fr.info803.trading_assistant.repository.AssetPredictionRepository;
import fr.info803.trading_assistant.repository.AssetRepository;

import fr.info803.trading_assistant.dto.AssetBacktestResultDto;
import fr.info803.trading_assistant.dto.GlobalBacktestStatsDto;
import fr.info803.trading_assistant.repository.projection.AssetBacktestResultProjection;

@ExtendWith(MockitoExtension.class)
class AssetPredictionServiceTest {

    @Mock
    private AssetDailyValueRepository assetDailyValueRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private PredictionFeatureService predictionFeatureService;

    @Mock
    private AiPredictionClient aiPredictionClient;

    @Mock
    private AssetPredictionRepository assetPredictionRepository;

    @InjectMocks
    private AssetPredictionService assetPredictionService;

    private Asset testAsset;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        testAsset = new Asset();
        testAsset.setId(1L);
        testAsset.setSymbol("BTC");

        testDate = LocalDate.of(2023, 10, 10);
    }

    @Test
    void generateAndSavePrediction_ShouldNotProceedIfInsufficientData() {
        // Arrange
        List<AssetDailyValue> insufficientCandles = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            AssetDailyValue candle = new AssetDailyValue();
            candle.setDate(testDate.minusDays(i));
            insufficientCandles.add(candle);
        }

        when(assetDailyValueRepository.findTop60ByAssetAndDateLessThanEqualOrderByDateDesc(testAsset, testDate))
                .thenReturn(insufficientCandles);

        // Act
        assetPredictionService.generateAndSavePrediction(testAsset, testDate);

        // Assert
        verify(predictionFeatureService, never()).calculateForLatestCandle(any());
        verify(aiPredictionClient, never()).predict(any());
        verify(assetPredictionRepository, never()).save(any());
    }

    @Test
    void generateAndSavePrediction_ShouldGenerateAndSaveNewPrediction() {
        // Arrange
        List<AssetDailyValue> sufficientCandles = new ArrayList<>();
        for (int i = 0; i < 55; i++) {
            AssetDailyValue candle = new AssetDailyValue();
            candle.setDate(testDate.minusDays(i));
            sufficientCandles.add(candle);
        }

        when(assetDailyValueRepository.findTop60ByAssetAndDateLessThanEqualOrderByDateDesc(testAsset, testDate))
                .thenReturn(sufficientCandles);

        PredictionFeaturesDto features = new PredictionFeaturesDto(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        when(predictionFeatureService.calculateForLatestCandle(any())).thenReturn(features);

        AiPredictionResponse aiResponse = new AiPredictionResponse(BigDecimal.valueOf(0.05), "UP");
        when(aiPredictionClient.predict(features)).thenReturn(aiResponse);

        when(assetPredictionRepository.findByAssetAndDate(testAsset, testDate))
                .thenReturn(Optional.empty());

        // Act
        assetPredictionService.generateAndSavePrediction(testAsset, testDate);

        // Assert
        verify(assetPredictionRepository).save(any(AssetPrediction.class));
    }

    @Test
    void generatePredictionsForDate_ShouldProcessAllAssets() {
        // Arrange
        Asset eth = new Asset();
        eth.setId(2L);
        eth.setSymbol("ETH");

        when(assetRepository.findAll()).thenReturn(List.of(testAsset, eth));

        // Act
        assetPredictionService.generatePredictionsForDate(testDate);

        // Assert
        verify(assetDailyValueRepository).findTop60ByAssetAndDateLessThanEqualOrderByDateDesc(testAsset, testDate);
        verify(assetDailyValueRepository).findTop60ByAssetAndDateLessThanEqualOrderByDateDesc(eth, testDate);
    }

    @Test
    void getGlobalPredictionStats_ShouldReturnZeroWhenNoData() {
        when(assetPredictionRepository.findAllPredictedVariations()).thenReturn(List.of());

        PredictionStatsDto stats = assetPredictionService.getGlobalPredictionStats();

        assertEquals(0L, stats.count());
        assertEquals(BigDecimal.ZERO, stats.min());
        assertEquals(BigDecimal.ZERO, stats.max());
        assertEquals(BigDecimal.ZERO, stats.mean());
        assertEquals(BigDecimal.ZERO, stats.median());
    }

    @Test
    void getGlobalPredictionStats_ShouldCalculateCorrectlyForOddSize() {
        List<BigDecimal> variations = List.of(
            BigDecimal.valueOf(0.01),
            BigDecimal.valueOf(-0.02),
            BigDecimal.valueOf(0.05),
            BigDecimal.valueOf(0.10),
            BigDecimal.valueOf(-0.01)
        ); // Sorted: -0.02, -0.01, 0.01, 0.05, 0.10

        when(assetPredictionRepository.findAllPredictedVariations()).thenReturn(variations);

        PredictionStatsDto stats = assetPredictionService.getGlobalPredictionStats();

        assertEquals(5L, stats.count());
        assertEquals(BigDecimal.valueOf(-0.02), stats.min());
        assertEquals(BigDecimal.valueOf(0.10), stats.max());
        
        // Sum = 0.13, Mean = 0.13 / 5 = 0.026
        assertEquals(BigDecimal.valueOf(0.026).setScale(6), stats.mean());
        
        // Median = 0.01
        assertEquals(BigDecimal.valueOf(0.01), stats.median());
    }

    @Test
    void getGlobalPredictionStats_ShouldCalculateCorrectlyForEvenSize() {
        List<BigDecimal> variations = List.of(
            BigDecimal.valueOf(0.01),
            BigDecimal.valueOf(-0.02),
            BigDecimal.valueOf(0.05),
            BigDecimal.valueOf(-0.01)
        ); // Sorted: -0.02, -0.01, 0.01, 0.05

        when(assetPredictionRepository.findAllPredictedVariations()).thenReturn(variations);

        PredictionStatsDto stats = assetPredictionService.getGlobalPredictionStats();

        assertEquals(4L, stats.count());
        assertEquals(BigDecimal.valueOf(-0.02), stats.min());
        assertEquals(BigDecimal.valueOf(0.05), stats.max());
        
        // Sum = 0.03, Mean = 0.03 / 4 = 0.0075
        assertEquals(BigDecimal.valueOf(0.0075).setScale(6), stats.mean());
        
        // Median = (-0.01 + 0.01) / 2 = 0.0
        assertEquals(BigDecimal.ZERO.setScale(6), stats.median());
    }
    @Test
    void evaluatePendingPredictions_ShouldCalculateCorrectlyForBullishPredictionAndHit() {
        // Arrange
        LocalDate targetDate = LocalDate.of(2023, 10, 11);
        LocalDate predictionDate = LocalDate.of(2023, 10, 10);
        
        when(assetRepository.findAll()).thenReturn(List.of(testAsset));
        
        AssetPrediction prediction = AssetPrediction.builder()
                .asset(testAsset)
                .date(predictionDate)
                .predictedVariation(BigDecimal.valueOf(2.0)) // +2%
                .build();
        when(assetPredictionRepository.findByAssetAndDate(testAsset, predictionDate))
                .thenReturn(Optional.of(prediction));
                
        AssetDailyValue candleTMinus1 = AssetDailyValue.builder()
                .close(BigDecimal.valueOf(100.0))
                .build();
        when(assetDailyValueRepository.findByAssetAndDate(testAsset, predictionDate))
                .thenReturn(Optional.of(candleTMinus1));
                
        AssetDailyValue candleT = AssetDailyValue.builder()
                .high(BigDecimal.valueOf(105.0)) // High is used since prediction is positive. Var is +5%
                .close(BigDecimal.valueOf(105.0))
                .build();
        when(assetDailyValueRepository.findByAssetAndDate(testAsset, targetDate))
                .thenReturn(Optional.of(candleT));
                
        // Act
        assetPredictionService.evaluatePendingPredictions(targetDate);
        
        // Assert
        ArgumentCaptor<AssetPrediction> captor = ArgumentCaptor.forClass(AssetPrediction.class);
        verify(assetPredictionRepository).save(captor.capture());
        
        AssetPrediction saved = captor.getValue();
        assertEquals(0, BigDecimal.valueOf(5.0).compareTo(saved.getActualVariation())); // 5% actual variation
        assertTrue(saved.getIsSuccess());
        assertEquals(0, BigDecimal.valueOf(3.0).compareTo(saved.getAbsoluteError())); // |2 - 5| = 3
    }
    
    @Test
    void evaluatePendingPredictions_ShouldCalculateCorrectlyForBearishPredictionAndHit() {
        // Arrange
        LocalDate targetDate = LocalDate.of(2023, 10, 11);
        LocalDate predictionDate = LocalDate.of(2023, 10, 10);
        
        when(assetRepository.findAll()).thenReturn(List.of(testAsset));
        
        AssetPrediction prediction = AssetPrediction.builder()
                .asset(testAsset)
                .date(predictionDate)
                .predictedVariation(BigDecimal.valueOf(-1.0)) // -1%
                .build();
        when(assetPredictionRepository.findByAssetAndDate(testAsset, predictionDate))
                .thenReturn(Optional.of(prediction));
                
        AssetDailyValue candleTMinus1 = AssetDailyValue.builder()
                .close(BigDecimal.valueOf(100.0))
                .build();
        when(assetDailyValueRepository.findByAssetAndDate(testAsset, predictionDate))
                .thenReturn(Optional.of(candleTMinus1));
                
        AssetDailyValue candleT = AssetDailyValue.builder()
                .low(BigDecimal.valueOf(98.0)) // Low is used since prediction is negative. Var is -2%
                .close(BigDecimal.valueOf(98.0))
                .build();
        when(assetDailyValueRepository.findByAssetAndDate(testAsset, targetDate))
                .thenReturn(Optional.of(candleT));
                
        // Act
        assetPredictionService.evaluatePendingPredictions(targetDate);
        
        // Assert
        ArgumentCaptor<AssetPrediction> captor = ArgumentCaptor.forClass(AssetPrediction.class);
        verify(assetPredictionRepository).save(captor.capture());
        
        AssetPrediction saved = captor.getValue();
        assertEquals(0, BigDecimal.valueOf(-2.0).compareTo(saved.getActualVariation())); // -2% actual variation
        assertTrue(saved.getIsSuccess());
        assertEquals(0, BigDecimal.valueOf(1.0).compareTo(saved.getAbsoluteError())); // |-1 - (-2)| = 1
    }

    @Test
    void evaluatePendingPredictions_ShouldCalculateCorrectlyForFalsePositive() {
        // Arrange
        LocalDate targetDate = LocalDate.of(2023, 10, 11);
        LocalDate predictionDate = LocalDate.of(2023, 10, 10);
        
        when(assetRepository.findAll()).thenReturn(List.of(testAsset));
        
        AssetPrediction prediction = AssetPrediction.builder()
                .asset(testAsset)
                .date(predictionDate)
                .predictedVariation(BigDecimal.valueOf(2.0)) // +2%
                .build();
        when(assetPredictionRepository.findByAssetAndDate(testAsset, predictionDate))
                .thenReturn(Optional.of(prediction));
                
        AssetDailyValue candleTMinus1 = AssetDailyValue.builder()
                .close(BigDecimal.valueOf(100.0))
                .build();
        when(assetDailyValueRepository.findByAssetAndDate(testAsset, predictionDate))
                .thenReturn(Optional.of(candleTMinus1));
                
        AssetDailyValue candleT = AssetDailyValue.builder()
                .high(BigDecimal.valueOf(105.0)) // High is 105 (+5%) but close is 99 (-1%)
                .close(BigDecimal.valueOf(99.0))
                .build();
        when(assetDailyValueRepository.findByAssetAndDate(testAsset, targetDate))
                .thenReturn(Optional.of(candleT));
                
        // Act
        assetPredictionService.evaluatePendingPredictions(targetDate);
        
        // Assert
        ArgumentCaptor<AssetPrediction> captor = ArgumentCaptor.forClass(AssetPrediction.class);
        verify(assetPredictionRepository).save(captor.capture());
        
        AssetPrediction saved = captor.getValue();
        assertEquals(0, BigDecimal.valueOf(-1.0).compareTo(saved.getActualVariation())); // -1% actual variation
        assertFalse(saved.getIsSuccess()); // Expected >0 but was <0
        assertEquals(0, BigDecimal.valueOf(3.0).compareTo(saved.getAbsoluteError())); // |2 - (-1)| = 3
    }

    @Test
    void getAssetBacktestResult_ShouldReturnResultWhenFound() {
        // Arrange
        String symbol = "BTC";
        AssetBacktestResultProjection projection = new AssetBacktestResultProjection() {
            @Override
            public String getSymbol() { return "BTC"; }
            @Override
            public Long getTotalPredictions() { return 50L; }
            @Override
            public BigDecimal getSuccessRatePct() { return BigDecimal.valueOf(70.0); }
            @Override
            public BigDecimal getMaxPotentialSuccessRatePct() { return BigDecimal.valueOf(80.0); }
            @Override
            public BigDecimal getMeanAbsoluteErrorPct() { return BigDecimal.valueOf(1.2); }
        };
        
        when(assetPredictionRepository.getAssetBacktestResultBySymbol(eq("BTC"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Optional.of(projection));

        // Act
        AssetBacktestResultDto result = assetPredictionService.getAssetBacktestResult(symbol, null, null);

        // Assert
        assertEquals("BTC", result.symbol());
        assertEquals(50L, result.totalPredictions());
        assertEquals(BigDecimal.valueOf(70.0), result.successRatePct());
        assertEquals(BigDecimal.valueOf(1.2), result.meanAbsoluteErrorPct());
    }

    @Test
    void getAssetBacktestResult_ShouldReturnEmptyResultWhenNotFound() {
        // Arrange
        String symbol = "UNKNOWN";
        
        when(assetPredictionRepository.getAssetBacktestResultBySymbol(eq("UNKNOWN"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        // Act
        AssetBacktestResultDto result = assetPredictionService.getAssetBacktestResult(symbol, null, null);

        // Assert
        assertEquals("UNKNOWN", result.symbol());
        assertEquals(0L, result.totalPredictions());
        assertEquals(BigDecimal.ZERO, result.successRatePct());
        assertEquals(BigDecimal.ZERO, result.meanAbsoluteErrorPct());
    }
}
