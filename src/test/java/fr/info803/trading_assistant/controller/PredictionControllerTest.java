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
}
