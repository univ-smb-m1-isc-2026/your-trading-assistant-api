package fr.info803.trading_assistant.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Component;

import fr.info803.trading_assistant.entity.Alert;
import fr.info803.trading_assistant.entity.AlertDirection;
import fr.info803.trading_assistant.entity.AlertType;
import fr.info803.trading_assistant.entity.AssetDailyValue;

/*
    Évaluateur d'alertes pour les seuils de prix (PRICE_THRESHOLD).

    Logique de comparaison :
    - ABOVE : compare candle.high >= threshold.
      On utilise le high (plus haut de la journée) car si le prix a touché
      le seuil à un moment de la journée, le high le capture même si le
      close est en dessous. C'est plus sensible qu'un simple check sur le close.
    - BELOW : compare candle.low <= threshold.
      Même raisonnement inversé : si le prix est passé sous le seuil à un
      moment de la journée, le low le capture.

    Pourquoi BigDecimal.compareTo() et pas equals() ?
    - BigDecimal.equals() compare aussi la scale : new BigDecimal("100.0")
      n'est PAS equals à new BigDecimal("100.00").
    - compareTo() compare uniquement la valeur numérique, ce qui est le
      comportement souhaité pour les comparaisons financières.
    - compareTo() >= 0 signifie "supérieur ou égal".
    - compareTo() <= 0 signifie "inférieur ou égal".
*/
@Component
public class PriceThresholdEvaluator implements AlertEvaluator {

    @Override
    public boolean supports(AlertType type) {
        return type == AlertType.PRICE_THRESHOLD;
    }

    @Override
    public Optional<BigDecimal> evaluate(Alert alert, AssetDailyValue candle) {
        BigDecimal threshold = alert.getThresholdValue();

        if (alert.getDirection() == AlertDirection.ABOVE) {
            // ABOVE : le prix le plus haut de la journée a-t-il atteint le seuil ?
            BigDecimal high = candle.getHigh();
            if (high.compareTo(threshold) >= 0) {
                return Optional.of(high);
            }
        } else {
            // BELOW : le prix le plus bas de la journée est-il passé sous le seuil ?
            BigDecimal low = candle.getLow();
            if (low.compareTo(threshold) <= 0) {
                return Optional.of(low);
            }
        }

        return Optional.empty();
    }
}
