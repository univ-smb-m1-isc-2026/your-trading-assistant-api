package fr.info803.trading_assistant.service;

import fr.info803.trading_assistant.dto.SentimentPollResponse;
import fr.info803.trading_assistant.dto.SentimentRequest;
import fr.info803.trading_assistant.dto.SentimentResponse;
import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.CommunitySentiment;
import fr.info803.trading_assistant.exception.AssetNotFoundException;
import fr.info803.trading_assistant.repository.AssetRepository;
import fr.info803.trading_assistant.repository.CommunitySentimentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunitySentimentService {

    private final CommunitySentimentRepository sentimentRepository;
    private final AssetRepository assetRepository;

    @Transactional
    public SentimentResponse putSentiment(Account account, String symbol, SentimentRequest request) {
        log.info("Account {} is voting {} for asset {}", account.getId(), request.type(), symbol);

        Asset asset = assetRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new AssetNotFoundException(symbol));

        CommunitySentiment sentiment = sentimentRepository.findByAccountAndAsset(account, asset)
                .orElseGet(() -> CommunitySentiment.builder()
                        .account(account)
                        .asset(asset)
                        .build());

        sentiment.setType(request.type());
        
        CommunitySentiment savedSentiment = sentimentRepository.save(sentiment);

        return new SentimentResponse(
                asset.getSymbol(),
                savedSentiment.getType(),
                savedSentiment.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public SentimentPollResponse getPollResults(String symbol) {
        String upperSymbol = symbol.toUpperCase();
        
        // Ensure asset exists first
        if (assetRepository.findBySymbol(upperSymbol).isEmpty()) {
            throw new AssetNotFoundException(upperSymbol);
        }

        return sentimentRepository.getPollResultsBySymbol(upperSymbol)
                .orElse(new SentimentPollResponse(upperSymbol, 0, 0));
    }

    @Transactional(readOnly = true)
    public SentimentResponse getUserSentiment(Account account, String symbol) {
        Asset asset = assetRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new AssetNotFoundException(symbol));

        return sentimentRepository.findByAccountAndAsset(account, asset)
                .map(sentiment -> new SentimentResponse(asset.getSymbol(), sentiment.getType(), sentiment.getUpdatedAt()))
                .orElse(null); // Or throw an exception, but returning 204 or empty is often better if no vote exists
    }
}
