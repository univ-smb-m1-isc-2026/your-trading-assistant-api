package fr.info803.trading_assistant.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import fr.info803.trading_assistant.dto.AiHealthResponse;
import fr.info803.trading_assistant.dto.AiPredictionResponse;
import fr.info803.trading_assistant.dto.PredictionFeaturesDto;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AiPredictionClient {

    private final RestClient restClient;

    public AiPredictionClient(@Value("${ai.api.url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public AiHealthResponse checkHealth() {
        try {
            return restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(AiHealthResponse.class);
        } catch (Exception e) {
            log.error("Failed to check AI API health", e);
            return new AiHealthResponse("unavailable");
        }
    }

    public AiPredictionResponse predict(PredictionFeaturesDto features) {
        try {
            return restClient.post()
                    .uri("/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(features)
                    .retrieve()
                    .body(AiPredictionResponse.class);
        } catch (Exception e) {
            log.error("Failed to fetch prediction from AI API", e);
            throw new RuntimeException("AI Prediction API call failed", e);
        }
    }
}
