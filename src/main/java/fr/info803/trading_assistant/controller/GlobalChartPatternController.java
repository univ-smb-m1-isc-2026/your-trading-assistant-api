package fr.info803.trading_assistant.controller;

import java.util.List;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fr.info803.trading_assistant.dto.ChartPatternResponse;
import fr.info803.trading_assistant.entity.ChartPatternCategory;
import fr.info803.trading_assistant.entity.ChartPatternType;
import fr.info803.trading_assistant.service.ChartPatternService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/patterns")
@RequiredArgsConstructor
public class GlobalChartPatternController {

    private final ChartPatternService chartPatternService;

    @GetMapping
    public ResponseEntity<Page<ChartPatternResponse>> getAllPatterns(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) ChartPatternType type,
            @RequestParam(required = false) ChartPatternCategory category,
            @PageableDefault(size = 50, sort = "date", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(chartPatternService.getAllPatterns(symbol, type, category, pageable));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<ChartPatternType, Long>> getStats(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) ChartPatternCategory category) {
        return ResponseEntity.ok(chartPatternService.getStats(symbol, category));
    }
}
