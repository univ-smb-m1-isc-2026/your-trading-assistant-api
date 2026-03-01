package fr.info803.trading_assistant.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Component;

import fr.info803.trading_assistant.entity.Alert;
import fr.info803.trading_assistant.entity.AlertDirection;
import fr.info803.trading_assistant.entity.AlertType;
import fr.info803.trading_assistant.entity.AssetDailyValue;

/*
    Évaluateur d'alertes pour les seuils de volume journalier (VOLUME_THRESHOLD).

    Logique de comparaison :
    - ABOVE : le volume de la journée est-il >= au seuil ?
      Cas d'usage : "Préviens-moi si le volume de BTC dépasse 50 000 unités"
      (signe d'une activité anormale ou d'un breakout).
    - BELOW : le volume de la journée est-il <= au seuil ?
      Cas d'usage : "Préviens-moi si le volume d'ETH tombe sous 10 000 unités"
      (signe d'un assèchement de liquidité).

    Contrairement à PriceThresholdEvaluator qui utilise high/low, ici on
    utilise directement candle.volume car il n'y a qu'une seule valeur de
    volume par journée (pas de distinction high/low pour le volume).
*/
@Component
public class VolumeThresholdEvaluator implements AlertEvaluator {

    @Override
    public boolean supports(AlertType type) {
        return type == AlertType.VOLUME_THRESHOLD;
    }

    @Override
    public Optional<BigDecimal> evaluate(Alert alert, AssetDailyValue candle) {
        BigDecimal threshold = alert.getThresholdValue();
        BigDecimal volume = candle.getVolume();

        if (alert.getDirection() == AlertDirection.ABOVE) {
            // ABOVE : le volume a-t-il atteint ou dépassé le seuil ?
            if (volume.compareTo(threshold) >= 0) {
                return Optional.of(volume);
            }
        } else {
            // BELOW : le volume est-il passé sous le seuil ?
            if (volume.compareTo(threshold) <= 0) {
                return Optional.of(volume);
            }
        }

        return Optional.empty();
    }
}
