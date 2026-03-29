package fr.info803.trading_assistant.dto;

import java.time.LocalDate;

import fr.info803.trading_assistant.entity.ChartPatternCategory;
import fr.info803.trading_assistant.entity.ChartPatternType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChartPatternResponse {
    private Long id;
    private String assetSymbol;
    private LocalDate date;
    private ChartPatternType type;
    private ChartPatternCategory category;
}
