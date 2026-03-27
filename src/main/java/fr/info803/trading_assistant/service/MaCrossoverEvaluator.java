package fr.info803.trading_assistant.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import fr.info803.trading_assistant.entity.Alert;
import fr.info803.trading_assistant.entity.AlertDirection;
import fr.info803.trading_assistant.entity.AlertType;
import fr.info803.trading_assistant.entity.AssetDailyValue;
import fr.info803.trading_assistant.repository.AssetDailyValueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
    Évaluateur d'alertes pour le croisement de moyennes mobiles (MA_CROSSOVER).

    Concept financier : un "croisement" se produit quand la position relative de
    deux moyennes mobiles change entre deux jours consécutifs.

    - Golden Cross (ABOVE) : la MA courte passe au-dessus de la MA longue.
      Signal haussier — le momentum court-terme dépasse le momentum long-terme.
      Jour J-1 : SMA(8) < SMA(50)  →  Jour J : SMA(8) >= SMA(50)

    - Death Cross (BELOW) : la MA courte passe en-dessous de la MA longue.
      Signal baissier — le momentum court-terme s'affaiblit.
      Jour J-1 : SMA(8) > SMA(50)  →  Jour J : SMA(8) <= SMA(50)

    L'utilisateur configure :
      - shortPeriod : fenêtre de la MA courte (ex: 8)
      - longPeriod  : fenêtre de la MA longue (ex: 30, 50)
      - maType      : "SMA" ou "EMA"
      - direction   : ABOVE (Golden Cross) ou BELOW (Death Cross)

    Pourquoi injecter AssetDailyValueRepository directement ?
      Comme prévu dans les commentaires de l'interface AlertEvaluator :
      "Si un futur évaluateur a besoin de plus de données (ex: historique de N jours
      pour un croisement de moyennes mobiles), il injectera le repository directement
      dans son constructeur @Component."
      On a besoin de (longPeriod + 1) jours de bougies pour calculer les MAs
      d'aujourd'hui ET d'hier (nécessaires pour détecter le croisement).

    Pourquoi ne pas réutiliser MovingAverageService.computeSma()/computeEma() ?
      Ces méthodes calculent une série complète (1 an de points). Ici on n'a besoin
      que de 2 valeurs (aujourd'hui et hier) pour chaque MA. Dupliquer une méthode
      de 5 lignes est préférable à un couplage inutile avec un service entier.

    Algorithmes de calcul :

    SMA (Simple Moving Average) :
      SMA(i) = (close(i) + close(i-1) + ... + close(i-period+1)) / period
      Chaque point a le même poids.

    EMA (Exponential Moving Average) :
      Facteur de lissage : k = 2 / (period + 1)
      EMA(period - 1) = SMA des `period` premiers close (amorçage)
      EMA(i) = close(i) * k + EMA(i-1) * (1 - k) pour i >= period
      Donne plus de poids aux prix récents → plus réactif mais plus de faux signaux.
*/
@Slf4j
@Component
@RequiredArgsConstructor
public class MaCrossoverEvaluator implements AlertEvaluator {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final AssetDailyValueRepository assetDailyValueRepository;

    @Override
    public boolean supports(AlertType type) {
        return type == AlertType.MA_CROSSOVER;
    }

    /*
        Évalue si un croisement de MAs s'est produit le jour de la bougie donnée.

        Étapes :
          1. Charger suffisamment de bougies historiques (longPeriod + buffer).
          2. Trouver les index de la bougie du jour et de la veille.
          3. Calculer les 4 valeurs de MA (2 périodes × 2 jours).
          4. Détecter si un croisement s'est produit entre hier et aujourd'hui.

        Retourne Optional<BigDecimal> :
          - present  = croisement détecté, la valeur est la MA courte du jour.
          - empty    = pas de croisement ou données insuffisantes.
    */
    @Override
    public Optional<BigDecimal> evaluate(Alert alert, AssetDailyValue candle) {
        int shortPeriod = alert.getShortPeriod();
        int longPeriod = alert.getLongPeriod();
        String maType = alert.getMaType();
        LocalDate today = candle.getDate();

        /*
            Pour détecter un croisement à la date J, on a besoin des MAs à J et J-1.
            Pour calculer la MA à J, on a besoin de `longPeriod` bougies se terminant à J.
            Pour calculer la MA à J-1, on a besoin de `longPeriod` bougies se terminant à J-1.
            Donc au total : longPeriod + 1 bougies minimum.

            Pour l'EMA, on a besoin de plus de données pour l'amorçage (la SMA initiale
            qui sert de seed). On charge un buffer supplémentaire pour la stabilité.
        */
        int bufferDays = "EMA".equalsIgnoreCase(maType) ? longPeriod * 2 : longPeriod + 10;
        LocalDate fromDate = today.minusDays(bufferDays);

        List<AssetDailyValue> candles = assetDailyValueRepository
            .findByAssetAndDateGreaterThanEqualOrderByDateAsc(alert.getAsset(), fromDate);

        // Besoin d'au moins longPeriod + 1 bougies pour avoir J et J-1
        if (candles.size() < longPeriod + 1) {
            log.debug("Not enough candles for MA crossover (need {}, have {})",
                longPeriod + 1, candles.size());
            return Optional.empty();
        }

        // Trouver l'index de la bougie du jour et de la veille
        int todayIndex = findCandleIndex(candles, today);
        if (todayIndex < 0) {
            log.debug("Candle for date {} not found in loaded data", today);
            return Optional.empty();
        }

        // La veille est la bougie juste avant dans la liste triée
        int yesterdayIndex = todayIndex - 1;
        if (yesterdayIndex < 0) {
            log.debug("No previous candle available for crossover detection");
            return Optional.empty();
        }

        // Vérifier qu'on a assez de données en amont pour calculer les MAs
        if (todayIndex < longPeriod - 1 || yesterdayIndex < longPeriod - 1) {
            log.debug("Not enough candles before today/yesterday index for MA period {}",
                longPeriod);
            return Optional.empty();
        }

        // Calcul des 4 valeurs de MA (courte et longue, pour hier et aujourd'hui)
        BigDecimal shortToday;
        BigDecimal longToday;
        BigDecimal shortYesterday;
        BigDecimal longYesterday;

        if ("EMA".equalsIgnoreCase(maType)) {
            shortToday = computeEmaAtIndex(candles, todayIndex, shortPeriod);
            longToday = computeEmaAtIndex(candles, todayIndex, longPeriod);
            shortYesterday = computeEmaAtIndex(candles, yesterdayIndex, shortPeriod);
            longYesterday = computeEmaAtIndex(candles, yesterdayIndex, longPeriod);
        } else {
            shortToday = computeSmaAtIndex(candles, todayIndex, shortPeriod);
            longToday = computeSmaAtIndex(candles, todayIndex, longPeriod);
            shortYesterday = computeSmaAtIndex(candles, yesterdayIndex, shortPeriod);
            longYesterday = computeSmaAtIndex(candles, yesterdayIndex, longPeriod);
        }

        // Si une des valeurs est null, données insuffisantes
        if (shortToday == null || longToday == null
                || shortYesterday == null || longYesterday == null) {
            return Optional.empty();
        }

        // Détection du croisement :
        // ABOVE (Golden Cross) : hier courte < longue, aujourd'hui courte >= longue
        boolean crossedAbove = shortYesterday.compareTo(longYesterday) < 0
                            && shortToday.compareTo(longToday) >= 0;

        // BELOW (Death Cross) : hier courte > longue, aujourd'hui courte <= longue
        boolean crossedBelow = shortYesterday.compareTo(longYesterday) > 0
                            && shortToday.compareTo(longToday) <= 0;

        if (alert.getDirection() == AlertDirection.ABOVE && crossedAbove) {
            log.info("Golden Cross detected for alert id={}: {}({})={} crossed above {}({})={}",
                alert.getId(), maType, shortPeriod, shortToday, maType, longPeriod, longToday);
            return Optional.of(shortToday);
        }

        if (alert.getDirection() == AlertDirection.BELOW && crossedBelow) {
            log.info("Death Cross detected for alert id={}: {}({})={} crossed below {}({})={}",
                alert.getId(), maType, shortPeriod, shortToday, maType, longPeriod, longToday);
            return Optional.of(shortToday);
        }

        return Optional.empty();
    }

    /*
        Cherche l'index d'une bougie par date dans la liste triée ASC.
        Recherche en partant de la fin car la date cible est généralement récente.
    */
    // package-private for testability via Mockito.spy()
    int findCandleIndex(List<AssetDailyValue> candles, LocalDate date) {
        for (int i = candles.size() - 1; i >= 0; i--) {
            if (candles.get(i).getDate().equals(date)) {
                return i;
            }
        }
        return -1;
    }

    /*
        Calcule la SMA à un index donné dans la liste de bougies.

        SMA = moyenne des `period` close se terminant à endIndex (inclus).
        Retourne null si pas assez de données.

        package-private pour testabilité via Mockito.spy().
    */
    // package-private for testability via Mockito.spy()
    BigDecimal computeSmaAtIndex(List<AssetDailyValue> candles, int endIndex, int period) {
        if (endIndex < period - 1) {
            return null;
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            sum = sum.add(candles.get(i).getClose());
        }
        return sum.divide(new BigDecimal(period), MC);
    }

    /*
        Calcule l'EMA à un index donné dans la liste de bougies.

        Algorithme :
          1. Amorçage : SMA des `period` premiers close comme seed EMA.
          2. Formule récursive : EMA(i) = close(i) * k + EMA(i-1) * (1 - k)
             avec k = 2 / (period + 1).
          3. On fait tourner la récursion jusqu'à endIndex.

        Retourne null si pas assez de données.

        package-private pour testabilité via Mockito.spy().
    */
    // package-private for testability via Mockito.spy()
    BigDecimal computeEmaAtIndex(List<AssetDailyValue> candles, int endIndex, int period) {
        if (endIndex < period - 1) {
            return null;
        }

        // Facteur de lissage
        BigDecimal k = new BigDecimal(2).divide(new BigDecimal(period + 1), MC);
        BigDecimal oneMinusK = BigDecimal.ONE.subtract(k, MC);

        // Amorçage : SMA des `period` premiers close
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(candles.get(i).getClose());
        }
        BigDecimal ema = sum.divide(new BigDecimal(period), MC);

        // Formule récursive de period à endIndex
        for (int i = period; i <= endIndex; i++) {
            BigDecimal close = candles.get(i).getClose();
            ema = close.multiply(k, MC).add(ema.multiply(oneMinusK, MC), MC);
        }

        return ema;
    }
}
