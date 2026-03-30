package fr.info803.trading_assistant.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import fr.info803.trading_assistant.client.AiPredictionClient;
import fr.info803.trading_assistant.dto.AiHealthResponse;
import fr.info803.trading_assistant.dto.AssetPredictionResponse;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetPrediction;
import fr.info803.trading_assistant.exception.GlobalExceptionHandler;
import fr.info803.trading_assistant.repository.AssetPredictionRepository;
import fr.info803.trading_assistant.service.AssetPredictionService;
import fr.info803.trading_assistant.dto.PredictionStatsDto;
import fr.info803.trading_assistant.dto.AssetBacktestResultDto;
import fr.info803.trading_assistant.dto.GlobalBacktestStatsDto;
import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
class PredictionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AiPredictionClient aiPredictionClient;

    @Mock
    private AssetPredictionRepository assetPredictionRepository;

    @Mock
    private AssetPredictionService assetPredictionService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PredictionController(aiPredictionClient, assetPredictionRepository, assetPredictionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    class HealthCheckTests {

        @Test
        void shouldReturnAiHealth() throws Exception {
            // Arrange
            when(aiPredictionClient.checkHealth()).thenReturn(new AiHealthResponse("ok"));

            // Act & Assert
            mockMvc.perform(get("/predictions/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ok"));
        }
    }

    @Nested
    class PredictionRetrievalTests {

        @Test
        void shouldReturnTopPredictions() throws Exception {
            // Arrange
            Asset btc = new Asset();
            btc.setSymbol("BTC");
            
            AssetPrediction prediction = new AssetPrediction();
            prediction.setId(1L);
            prediction.setAsset(btc);
            prediction.setDate(LocalDate.now());
            prediction.setPredictedVariation(java.math.BigDecimal.valueOf(0.05));

            when(assetPredictionRepository.findTopPredictionsByDateOrderByAbsoluteVariationDesc(any(LocalDate.class), any(Pageable.class)))
                    .thenReturn(List.of(prediction));

            // Act & Assert
            mockMvc.perform(get("/predictions/top?limit=10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].symbol").value("BTC"))
                    .andExpect(jsonPath("$[0].predictedVariationPct").value(0.05))
                    .andExpect(jsonPath("$[0].expectedDirection").value("UP"));
        }

        @Test
        void shouldReturnAssetPredictions() throws Exception {
            // Arrange
            Asset eth = new Asset();
            eth.setSymbol("ETH");

            AssetPrediction prediction = new AssetPrediction();
            prediction.setId(2L);
            prediction.setAsset(eth);
            prediction.setDate(LocalDate.of(2023, 10, 10));
            prediction.setPredictedVariation(java.math.BigDecimal.valueOf(-0.02));

            when(assetPredictionRepository.findByAssetSymbolOrderByDateDesc("ETH"))
                    .thenReturn(List.of(prediction));

            // Act & Assert
            mockMvc.perform(get("/predictions/ETH"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].symbol").value("ETH"))
                    .andExpect(jsonPath("$[0].predictedVariationPct").value(-0.02))
                    .andExpect(jsonPath("$[0].expectedDirection").value("DOWN"));
        }

        @Test
        void shouldReturnGlobalStats() throws Exception {
            // Arrange
            PredictionStatsDto stats = new PredictionStatsDto(
                    BigDecimal.valueOf(-0.1),
                    BigDecimal.valueOf(0.2),
                    BigDecimal.valueOf(0.05),
                    BigDecimal.valueOf(0.01),
                    100L
            );
            when(assetPredictionService.getGlobalPredictionStats()).thenReturn(stats);

            // Act & Assert
            mockMvc.perform(get("/predictions/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.min").value(-0.1))
                    .andExpect(jsonPath("$.max").value(0.2))
                    .andExpect(jsonPath("$.mean").value(0.05))
                    .andExpect(jsonPath("$.median").value(0.01))
                    .andExpect(jsonPath("$.count").value(100));
        }
    }
    @Nested
    class BacktestTests {

        @Test
        void shouldReturnAssetBacktestResults() throws Exception {
            // Arrange
            AssetBacktestResultDto dto = new AssetBacktestResultDto("BTC", 100L, BigDecimal.valueOf(65.0), BigDecimal.valueOf(75.0), BigDecimal.valueOf(1.5));
            when(assetPredictionService.getAssetBacktestResults(null, null)).thenReturn(List.of(dto));

            // Act & Assert
            mockMvc.perform(get("/predictions/backtest/assets"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].symbol").value("BTC"))
                    .andExpect(jsonPath("$[0].totalPredictions").value(100))
                    .andExpect(jsonPath("$[0].successRatePct").value(65.0))
                    .andExpect(jsonPath("$[0].maxPotentialSuccessRatePct").value(75.0))
                    .andExpect(jsonPath("$[0].meanAbsoluteErrorPct").value(1.5));
        }

        @Test
        void shouldReturnAssetBacktestResult() throws Exception {
            // Arrange
            AssetBacktestResultDto dto = new AssetBacktestResultDto("BTC", 50L, BigDecimal.valueOf(70.0), BigDecimal.valueOf(80.0), BigDecimal.valueOf(1.2));
            when(assetPredictionService.getAssetBacktestResult("BTC", null, null)).thenReturn(dto);

            // Act & Assert
            mockMvc.perform(get("/predictions/backtest/assets/BTC"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.symbol").value("BTC"))
                    .andExpect(jsonPath("$.totalPredictions").value(50))
                    .andExpect(jsonPath("$.successRatePct").value(70.0))
                    .andExpect(jsonPath("$.maxPotentialSuccessRatePct").value(80.0))
                    .andExpect(jsonPath("$.meanAbsoluteErrorPct").value(1.2));
        }

        @Test
        void shouldReturnGlobalBacktestStats() throws Exception {
            // Arrange
            GlobalBacktestStatsDto dto = new GlobalBacktestStatsDto(500L, BigDecimal.valueOf(60.5), BigDecimal.valueOf(70.5), BigDecimal.valueOf(2.0));
            when(assetPredictionService.getGlobalBacktestStats(null, null)).thenReturn(dto);

            // Act & Assert
            mockMvc.perform(get("/predictions/backtest/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalPredictions").value(500))
                    .andExpect(jsonPath("$.successRatePct").value(60.5))
                    .andExpect(jsonPath("$.maxPotentialSuccessRatePct").value(70.5))
                    .andExpect(jsonPath("$.meanAbsoluteErrorPct").value(2.0));
        }
    }
}
