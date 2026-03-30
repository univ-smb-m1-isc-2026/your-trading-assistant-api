package fr.info803.trading_assistant.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import fr.info803.trading_assistant.dto.AssetBacktestResultDto;
import fr.info803.trading_assistant.dto.GlobalBacktestStatsDto;
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

/**
 * Controller for managing AI predictions.
 */
@RestController
@RequestMapping("/predictions")
@RequiredArgsConstructor
public class PredictionController {

    /** Client to call the external AI prediction service. */
    private final AiPredictionClient aiPredictionClient;

    /** Repository for asset predictions. */
    private final AssetPredictionRepository assetPredictionRepository;

    /** Service for asset predictions. */
    private final AssetPredictionService assetPredictionService;

    /**
     * Checks the health of the external AI service.
     *
     * @return a ResponseEntity containing the health response
     */
    @GetMapping("/health")
    public ResponseEntity<AiHealthResponse> checkAiHealth() {
        return ResponseEntity.ok(aiPredictionClient.checkHealth());
    }

    /**
     * Retrieves the top predictions for a specific date.
     *
     * @param limit the max number of predictions to return
     * @param date the date for which to retrieve predictions
     * @return a ResponseEntity with a list of AssetPredictionResponse
     */
    @GetMapping("/top")
    public ResponseEntity<List<AssetPredictionResponse>> getTopPredictions(
            @RequestParam(defaultValue = "10") final int limit,
            @RequestParam(required = false) final LocalDate date
    ) {
        LocalDate queryDate = date;
        if (queryDate == null) {
            queryDate = LocalDate.now();
        }

        List<AssetPredictionResponse> responses = assetPredictionRepository
                .findTopPredictionsByDateOrderByAbsoluteVariationDesc(
                        queryDate, PageRequest.of(0, limit))
                .stream()
                .map(AssetPredictionResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Retrieves all predictions for a given asset symbol.
     *
     * @param symbol the asset symbol
     * @return a ResponseEntity with a list of AssetPredictionResponse
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<List<AssetPredictionResponse>> getAssetPredictions(
            @PathVariable final String symbol) {
        List<AssetPredictionResponse> responses = assetPredictionRepository
                .findByAssetSymbolOrderByDateDesc(symbol.toUpperCase())
                .stream()
                .map(AssetPredictionResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Retrieves global statistics about the predictions.
     *
     * @return a ResponseEntity containing global prediction statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<PredictionStatsDto> getGlobalStats() {
        return ResponseEntity.ok(
                assetPredictionService.getGlobalPredictionStats());
    }

    /**
     * Retrieves backtest results grouped by asset within a date range.
     *
     * @param startDate the starting date of the backtest range
     * @param endDate the ending date of the backtest range
     * @return a ResponseEntity containing a list of backtest results by asset
     */
    @GetMapping("/backtest/assets")
    public ResponseEntity<List<AssetBacktestResultDto>> getAssetBacktestResults(
            @RequestParam(required = false) final LocalDate startDate,
            @RequestParam(required = false) final LocalDate endDate
    ) {
        return ResponseEntity.ok(
                assetPredictionService.getAssetBacktestResults(
                        startDate, endDate));
    }

    /**
     * Retrieves backtest results for a specific asset within a date range.
     *
     * @param symbol    the asset symbol
     * @param startDate the starting date of the backtest range
     * @param endDate   the ending date of the backtest range
     * @return a ResponseEntity containing the backtest result for the asset
     */
    @GetMapping("/backtest/assets/{symbol}")
    public ResponseEntity<AssetBacktestResultDto> getAssetBacktestResult(
            @PathVariable final String symbol,
            @RequestParam(required = false) final LocalDate startDate,
            @RequestParam(required = false) final LocalDate endDate
    ) {
        return ResponseEntity.ok(
                assetPredictionService.getAssetBacktestResult(
                        symbol, startDate, endDate));
    }

    /**
     * Retrieves global backtest statistics within a date range.
     *
     * @param startDate the starting date of the backtest range
     * @param endDate the ending date of the backtest range
     * @return a ResponseEntity containing the global backtest statistics
     */
    @GetMapping("/backtest/stats")
    public ResponseEntity<GlobalBacktestStatsDto> getGlobalBacktestStats(
            @RequestParam(required = false) final LocalDate startDate,
            @RequestParam(required = false) final LocalDate endDate
    ) {
        return ResponseEntity.ok(
                assetPredictionService.getGlobalBacktestStats(
                        startDate, endDate));
    }
}
