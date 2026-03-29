package fr.info803.trading_assistant.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;

/*
    Service qui communique avec le micro-service IA (Python FastAPI).

    Le micro-service IA est accessible uniquement via le réseau Docker interne
    yta-internal. L'URL est configurée via la variable d'environnement YTA_ML_URL.

    Endpoints appelés :
      - GET  /health  → vérifier que le service IA est opérationnel
      - POST /predict → envoyer les features et recevoir la prédiction
*/
@Service
@Slf4j
public class AiPredictionService {

    private final WebClient webClient;

    public AiPredictionService(
            WebClient.Builder webClientBuilder,
            @Value("${yta.ml.url:http://your-trading-assistant-ai:8000}") String mlUrl) {
        this.webClient = webClientBuilder.baseUrl(mlUrl).build();
    }

    /*
        Vérifie que le service IA est opérationnel.
        Retourne true si le service répond {"status": "ok"}, false sinon.
    */
    public boolean isHealthy() {
        try {
            Map<String, String> response = webClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {})
                    .block();
            return response != null && "ok".equals(response.get("status"));
        } catch (Exception e) {
            log.warn("Service IA indisponible : {}", e.getMessage());
            return false;
        }
    }

    /*
        Envoie les features au service IA et retourne la prédiction.
        Retourne une Map avec predicted_variation_pct et direction.
    */
    public Map<String, Object> predict(Map<String, Double> features) {
        return webClient.post()
                .uri("/predict")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(features)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    /*
        Récupère le rapport de test du modèle (test.log parsé par le service IA).
    */
    public Map<String, Object> getTestReport() {
        return webClient.get()
                .uri("/test-report")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    /*
        Récupère un exemple d'input récent (dernier jour du test set, actif aléatoire).
    */
    public Map<String, Object> getLatestSample() {
        return webClient.get()
                .uri("/latest-sample")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }
}
