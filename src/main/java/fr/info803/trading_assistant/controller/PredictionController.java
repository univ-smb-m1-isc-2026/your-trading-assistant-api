package fr.info803.trading_assistant.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fr.info803.trading_assistant.client.AiPredictionClient;
import fr.info803.trading_assistant.dto.AiHealthResponse;
import fr.info803.trading_assistant.dto.AssetPredictionResponse;
import fr.info803.trading_assistant.dto.PredictionStatsDto;
import fr.info803.trading_assistant.repository.AssetPredictionRepository;
import fr.info803.trading_assistant.service.AssetPredictionService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/predictions")
@RequiredArgsConstructor
public class PredictionController {

    private final AiPredictionClient aiPredictionClient;
    private final AssetPredictionRepository assetPredictionRepository;
    private final AssetPredictionService assetPredictionService;

    @GetMapping("/health")
    public ResponseEntity<AiHealthResponse> checkAiHealth() {
        return ResponseEntity.ok(aiPredictionClient.checkHealth());
    }

    @GetMapping("/top")
    public ResponseEntity<List<AssetPredictionResponse>> getTopPredictions(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) LocalDate date
    ) {
        if (date == null) {
            date = LocalDate.now();
        }

        List<AssetPredictionResponse> responses = assetPredictionRepository
                .findTopPredictionsByDateOrderByAbsoluteVariationDesc(date, PageRequest.of(0, limit))
                .stream()
                .map(AssetPredictionResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<List<AssetPredictionResponse>> getAssetPredictions(@PathVariable String symbol) {
        List<AssetPredictionResponse> responses = assetPredictionRepository
                .findByAssetSymbolOrderByDateDesc(symbol.toUpperCase())
                .stream()
                .map(AssetPredictionResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/stats")
    public ResponseEntity<PredictionStatsDto> getGlobalStats() {
        return ResponseEntity.ok(assetPredictionService.getGlobalPredictionStats());
    }
}
