package fr.info803.trading_assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import fr.info803.trading_assistant.entity.AssetDailyValue;
import fr.info803.trading_assistant.entity.ChartPattern;
import fr.info803.trading_assistant.entity.ChartPatternType;
import fr.info803.trading_assistant.repository.AssetDailyValueRepository;
import fr.info803.trading_assistant.repository.AssetRepository;
import fr.info803.trading_assistant.repository.ChartPatternRepository;

class ChartPatternServiceTest {

    private ChartPatternService service;

    @BeforeEach
    void setUp() {
        service = new ChartPatternService(
            mock(ChartPatternRepository.class),
            mock(AssetRepository.class),
            mock(AssetDailyValueRepository.class)
        );
    }

    @Test
    @DisplayName("should return all patterns when filters are null")
    void shouldReturnAllPatternsWhenFiltersAreNull() {
        // Arrange
        ChartPatternRepository repository = mock(ChartPatternRepository.class);
        ChartPatternService serviceWithMock = new ChartPatternService(
            repository,
            mock(AssetRepository.class),
            mock(AssetDailyValueRepository.class)
        );

        Page<ChartPattern> page = new PageImpl<>(Collections.emptyList());
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        // Act
        serviceWithMock.getAllPatterns(null, null, null, PageRequest.of(0, 10));

        // Assert
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    private AssetDailyValue createCandle(String open, String high, String low, String close) {
        return AssetDailyValue.builder()
            .date(LocalDate.now())
            .open(new BigDecimal(open))
            .high(new BigDecimal(high))
            .low(new BigDecimal(low))
            .close(new BigDecimal(close))
            .volume(BigDecimal.TEN)
            .build();
    }

    @Test
    void shouldDetectBullishEngulfing() {
        // prev1: red candle
        AssetDailyValue prev = createCandle("100", "105", "85", "90");
        // current: green candle engulfing prev1 (O2 <= C1 AND C2 >= O1) -> (O2 <= 90 AND C2 >= 100)
        AssetDailyValue current = createCandle("88", "110", "80", "105");

        List<ChartPatternType> patterns = service.detectPatterns(List.of(prev, current));

        assertThat(patterns).contains(ChartPatternType.BULLISH_ENGULFING);
    }

    @Test
    void shouldDetectBearishEngulfing() {
        // prev1: green candle
        AssetDailyValue prev = createCandle("90", "105", "85", "100");
        // current: red candle engulfing prev1 (O2 >= C1 AND C2 <= O1) -> (O2 >= 100 AND C2 <= 90)
        AssetDailyValue current = createCandle("105", "110", "80", "85");

        List<ChartPatternType> patterns = service.detectPatterns(List.of(prev, current));

        assertThat(patterns).contains(ChartPatternType.BEARISH_ENGULFING);
    }

    @Test
    void shouldDetectDoji() {
        // open and close are very close (diff <= 5% of range)
        AssetDailyValue doji = createCandle("100", "110", "90", "100.5");

        List<ChartPatternType> patterns = service.detectPatterns(List.of(doji));

        assertThat(patterns).contains(ChartPatternType.DOJI);
    }

    @Test
    void shouldDetectHammer() {
        // Long lower wick, small body at top, almost no upper wick
        // O=100, C=105, H=106, L=50
        // Body = 5, Lower Wick = 100-50 = 50, Upper Wick = 106-105 = 1, Range = 56
        AssetDailyValue hammer = createCandle("100", "106", "50", "105");

        List<ChartPatternType> patterns = service.detectPatterns(List.of(hammer));

        assertThat(patterns).contains(ChartPatternType.HAMMER);
        assertThat(patterns).doesNotContain(ChartPatternType.SHOOTING_STAR);
    }

    @Test
    void shouldDetectShootingStar() {
        // Long upper wick, small body at bottom, almost no lower wick
        // O=50, C=45, H=100, L=44
        // Body = 5, Lower wick = 45-44 = 1, Upper wick = 100-50 = 50, Range = 56
        AssetDailyValue star = createCandle("50", "100", "44", "45");

        List<ChartPatternType> patterns = service.detectPatterns(List.of(star));

        assertThat(patterns).contains(ChartPatternType.SHOOTING_STAR);
        assertThat(patterns).doesNotContain(ChartPatternType.HAMMER);
    }
}
