package fr.info803.trading_assistant.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.info803.trading_assistant.service.AiPredictionService;
import lombok.RequiredArgsConstructor;

/*
    Contrôleur REST pour les endpoints IA.

    Routes :

    1. GET /ai/health
       - Vérifie que le service IA est opérationnel.
       - Réponse : { "status": "ok" } ou { "status": "unavailable" }

    2. POST /ai/predict
       - Envoie les features au service IA et retourne la prédiction.
       - Body : les features sous forme de Map<String, Double>
       - Réponse : { "predicted_variation_pct": 0.28, "direction": "UP" }
*/
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiPredictionService aiPredictionService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        boolean healthy = aiPredictionService.isHealthy();
        String status = healthy ? "ok" : "unavailable";
        return ResponseEntity.ok(Map.of("status", status));
    }

    @PostMapping("/predict")
    public ResponseEntity<Map<String, Object>> predict(@RequestBody Map<String, Double> features) {
        return ResponseEntity.ok(aiPredictionService.predict(features));
    }

    @GetMapping("/test-report")
    public ResponseEntity<Map<String, Object>> testReport() {
        return ResponseEntity.ok(aiPredictionService.getTestReport());
    }

    @GetMapping("/latest-sample")
    public ResponseEntity<Map<String, Object>> latestSample() {
        return ResponseEntity.ok(aiPredictionService.getLatestSample());
    }
}
