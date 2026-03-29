package fr.info803.trading_assistant.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import fr.info803.trading_assistant.dto.ChartPatternResponse;
import fr.info803.trading_assistant.entity.ChartPatternCategory;
import fr.info803.trading_assistant.entity.ChartPatternType;
import fr.info803.trading_assistant.exception.GlobalExceptionHandler;
import fr.info803.trading_assistant.service.ChartPatternService;

/**
 * Unit tests for GlobalChartPatternController.
 */
@DisplayName("GlobalChartPatternController Unit Tests")
@ExtendWith(MockitoExtension.class)
class GlobalChartPatternControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ChartPatternService chartPatternService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new GlobalChartPatternController(chartPatternService))
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("should return 200 with paginated list of all patterns")
    void shouldReturnAllPatterns() throws Exception {
        // Arrange
        ChartPatternResponse response = ChartPatternResponse.builder()
            .id(1L)
            .assetSymbol("BTC")
            .date(LocalDate.of(2026, 3, 29))
            .type(ChartPatternType.BULLISH_ENGULFING)
            .category(ChartPatternCategory.BULLISH)
            .build();
        
        Page<ChartPatternResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 50), 1);
        
        when(chartPatternService.getAllPatterns(any(), any(), any(), any(Pageable.class)))
            .thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/patterns"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].assetSymbol").value("BTC"))
            .andExpect(jsonPath("$.content[0].type").value("BULLISH_ENGULFING"));
    }

    @Test
    @DisplayName("should return 200 with filtered patterns by partial symbol match")
    void shouldReturnFilteredBySymbol() throws Exception {
        // Arrange
        ChartPatternResponse response = ChartPatternResponse.builder()
            .id(1L)
            .assetSymbol("BTC")
            .date(LocalDate.of(2026, 3, 29))
            .type(ChartPatternType.BULLISH_ENGULFING)
            .category(ChartPatternCategory.BULLISH)
            .build();
        
        Page<ChartPatternResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 50), 1);
        
        // Use "bt" to test partial and case-insensitive match
        when(chartPatternService.getAllPatterns(eq("bt"), any(), any(), any(Pageable.class)))
            .thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/patterns").param("symbol", "bt"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].assetSymbol").value("BTC"));
    }

    @Test
    @DisplayName("should return 200 with filtered patterns by type")
    void shouldReturnFilteredByType() throws Exception {
        // Arrange
        ChartPatternResponse response = ChartPatternResponse.builder()
            .id(1L)
            .assetSymbol("BTC")
            .date(LocalDate.of(2026, 3, 29))
            .type(ChartPatternType.HAMMER)
            .category(ChartPatternCategory.BULLISH)
            .build();
        
        Page<ChartPatternResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 50), 1);
        
        when(chartPatternService.getAllPatterns(any(), eq(ChartPatternType.HAMMER), any(), any(Pageable.class)))
            .thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/patterns").param("type", "HAMMER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].type").value("HAMMER"));
    }

    @Test
    @DisplayName("should return 200 with filtered patterns by category")
    void shouldReturnFilteredByCategory() throws Exception {
        // Arrange
        ChartPatternResponse response = ChartPatternResponse.builder()
            .id(1L)
            .assetSymbol("BTC")
            .date(LocalDate.of(2026, 3, 29))
            .type(ChartPatternType.BULLISH_ENGULFING)
            .category(ChartPatternCategory.BULLISH)
            .build();
        
        Page<ChartPatternResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 50), 1);
        
        when(chartPatternService.getAllPatterns(any(), any(), eq(ChartPatternCategory.BULLISH), any(Pageable.class)))
            .thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/patterns").param("category", "BULLISH"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].category").value("BULLISH"));
    }

    @Test
    @DisplayName("should return 200 with statistics by type")
    void shouldReturnStats() throws Exception {
        // Arrange
        Map<ChartPatternType, Long> stats = Map.of(
            ChartPatternType.BULLISH_ENGULFING, 45L,
            ChartPatternType.HAMMER, 32L
        );
        
        when(chartPatternService.getStats(any(), any())).thenReturn(stats);

        // Act & Assert
        mockMvc.perform(get("/patterns/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.BULLISH_ENGULFING").value(45))
            .andExpect(jsonPath("$.HAMMER").value(32));
    }

    @Test
    @DisplayName("should return 400 when type is invalid")
    void shouldReturn400ForInvalidType() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/patterns").param("type", "INVALID_TYPE"))
            .andExpect(status().isBadRequest());
    }
}
