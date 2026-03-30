package fr.info803.trading_assistant.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
}
