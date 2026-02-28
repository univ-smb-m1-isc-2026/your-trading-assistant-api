package fr.info803.trading_assistant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssetSummaryResponse {

    private String symbol;
    private BigDecimal lastPrice;
    private LocalDate lastDate;
}
