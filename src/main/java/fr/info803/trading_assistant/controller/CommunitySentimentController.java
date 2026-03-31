package fr.info803.trading_assistant.controller;

import fr.info803.trading_assistant.dto.SentimentPollResponse;
import fr.info803.trading_assistant.dto.SentimentRequest;
import fr.info803.trading_assistant.dto.SentimentResponse;
import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.service.CommunitySentimentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/assets/{symbol}/sentiments")
@RequiredArgsConstructor
public class CommunitySentimentController {

    private final CommunitySentimentService sentimentService;

    @PutMapping("/me")
    public ResponseEntity<SentimentResponse> putSentiment(
            @AuthenticationPrincipal Account account,
            @PathVariable String symbol,
            @Valid @RequestBody SentimentRequest request) {
        SentimentResponse response = sentimentService.putSentiment(account, symbol, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/poll")
    public ResponseEntity<SentimentPollResponse> getPollResults(@PathVariable String symbol) {
        SentimentPollResponse response = sentimentService.getPollResults(symbol);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<SentimentResponse> getMySentiment(
            @AuthenticationPrincipal Account account,
            @PathVariable String symbol) {
        SentimentResponse response = sentimentService.getUserSentiment(account, symbol);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }
}
