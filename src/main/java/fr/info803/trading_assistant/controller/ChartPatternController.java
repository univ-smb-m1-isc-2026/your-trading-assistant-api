package fr.info803.trading_assistant.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fr.info803.trading_assistant.dto.ChartPatternResponse;
import fr.info803.trading_assistant.service.ChartPatternService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/assets/{symbol}/patterns")
@RequiredArgsConstructor
public class ChartPatternController {

    private final ChartPatternService chartPatternService;

    @GetMapping
    public ResponseEntity<List<ChartPatternResponse>> getPatterns(
            @PathVariable String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        List<ChartPatternResponse> patterns;
        if (date != null) {
            patterns = chartPatternService.getPatternsForAssetAndDate(symbol, date);
        } else {
            patterns = chartPatternService.getPatternsForAsset(symbol);
        }
        
        return ResponseEntity.ok(patterns);
    }
    
    @GetMapping("/today")
    public ResponseEntity<List<ChartPatternResponse>> getPatternsForToday(@PathVariable String symbol) {
        // "Today" du point de vue des bougies : souvent J-1 ou la date de la dernière bougie synchronisée.
        // On prend J-1 par défaut ou LocalDate.now()
        List<ChartPatternResponse> patterns = chartPatternService.getPatternsForAssetAndDate(symbol, LocalDate.now().minusDays(1));
        return ResponseEntity.ok(patterns);
    }
}
