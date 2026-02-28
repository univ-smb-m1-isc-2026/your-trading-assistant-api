package fr.info803.trading_assistant.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import fr.info803.trading_assistant.dto.MovingAveragePointResponse;
import fr.info803.trading_assistant.dto.MovingAverageSeriesResponse;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetDailyValue;
import fr.info803.trading_assistant.exception.AssetNotFoundException;
import fr.info803.trading_assistant.exception.InvalidMovingAverageRequestException;
import fr.info803.trading_assistant.repository.AssetDailyValueRepository;
import fr.info803.trading_assistant.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
    Service de calcul des moyennes mobiles (SMA et EMA) à la volée.

    Les MAs ne sont PAS persistées en base : elles sont dérivées des bougies
    existantes dans AssetDailyValue à chaque appel. Cela évite la dénormalisation,
    les problèmes de synchronisation et offre une flexibilité totale sur les périodes.

    Contrats :

    1. getMovingAverages(String symbol, String type, List<Integer> periods)
       - Résout le symbol en Asset (ou lève AssetNotFoundException).
       - Valide le type ("SMA" ou "EMA", insensible à la casse).
       - Valide les périodes (chacune doit être >= 1).
       - Charge les candles sur les 12 derniers mois (ASC par date).
       - Pour chaque période demandée, calcule la série de MA correspondante.
       - Retourne une List<MovingAverageSeriesResponse>.

    Algorithmes :

    SMA (Simple Moving Average) :
      Pour chaque date i (i >= period - 1) :
        SMA(i) = (close(i) + close(i-1) + ... + close(i-period+1)) / period

    EMA (Exponential Moving Average) :
      Facteur de lissage : k = 2 / (period + 1)
      EMA(period - 1) = SMA des `period` premiers close (amorçage)
      Pour chaque date i (i >= period) :
        EMA(i) = close(i) * k + EMA(i-1) * (1 - k)

    Pourquoi BigDecimal et MathContext.DECIMAL64 ?
      Les calculs financiers exigent une précision exacte. Les double introduisent
      des erreurs d'arrondi (ex: 0.1 + 0.2 != 0.3). DECIMAL64 offre 16 chiffres
      significatifs, suffisants pour les prix crypto (jusqu'à 10 décimales en base).
*/
@Slf4j
@Service
@RequiredArgsConstructor
public class MovingAverageService {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final AssetRepository assetRepository;
    private final AssetDailyValueRepository assetDailyValueRepository;

    /*
        Point d'entrée principal.

        Paramètres :
          - symbol  : symbole de l'asset (ex: "BTC")
          - type    : "SMA" ou "EMA" (insensible à la casse)
          - periods : liste de fenêtres de calcul (ex: [20, 50])

        Retourne une série de MA par période demandée.
        Lève AssetNotFoundException si le symbol est inconnu.
        Lève InvalidMovingAverageRequestException si type ou periods invalides.
    */
    public List<MovingAverageSeriesResponse> getMovingAverages(String symbol, String type, List<Integer> periods) {
        log.info("Computing {} moving averages for symbol={}, periods={}", type, symbol, periods);

        // Validation du type
        String normalizedType = type.toUpperCase();
        if (!"SMA".equals(normalizedType) && !"EMA".equals(normalizedType)) {
            throw new InvalidMovingAverageRequestException(
                "Invalid moving average type: '" + type + "'. Supported types: SMA, EMA.");
        }

        // Validation des périodes
        if (periods == null || periods.isEmpty()) {
            throw new InvalidMovingAverageRequestException(
                "At least one period is required.");
        }
        for (Integer period : periods) {
            if (period == null || period < 1) {
                throw new InvalidMovingAverageRequestException(
                    "Invalid period: " + period + ". Each period must be >= 1.");
            }
        }

        // Résolution du symbol en asset
        Asset asset = assetRepository.findBySymbol(symbol)
            .orElseThrow(() -> new AssetNotFoundException(symbol));

        // Chargement des candles (12 derniers mois, triés date ASC)
        LocalDate fromDate = LocalDate.now().minusYears(1);
        List<AssetDailyValue> candles = assetDailyValueRepository
            .findByAssetAndDateGreaterThanEqualOrderByDateAsc(asset, fromDate);

        // Calcul d'une série par période
        List<MovingAverageSeriesResponse> result = new ArrayList<>();
        for (int period : periods) {
            List<MovingAveragePointResponse> points;
            if ("SMA".equals(normalizedType)) {
                points = computeSma(candles, period);
            } else {
                points = computeEma(candles, period);
            }
            result.add(MovingAverageSeriesResponse.builder()
                .type(normalizedType)
                .period(period)
                .values(points)
                .build());
        }

        log.info("Computed {} series for symbol={}", result.size(), symbol);
        return result;
    }

    /*
        Calcule la Simple Moving Average (SMA) pour une période donnée.

        Algorithme à fenêtre glissante (somme cumulative) :
          - On maintient une somme courante des `period` derniers close.
          - À chaque pas, on ajoute le nouveau close et on retire l'ancien.
          - Complexité : O(n) au lieu de O(n * period) avec l'approche naïve.

        Si le nombre de candles est inférieur à la période, retourne une liste vide
        (pas assez de données pour calculer même un seul point).

        package-private pour testabilité via Mockito.spy()
    */
    List<MovingAveragePointResponse> computeSma(List<AssetDailyValue> candles, int period) {
        if (candles.size() < period) {
            return List.of();
        }

        List<MovingAveragePointResponse> points = new ArrayList<>();
        BigDecimal periodBd = new BigDecimal(period);

        // Somme initiale des `period` premiers close
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(candles.get(i).getClose());
        }

        // Premier point SMA
        points.add(MovingAveragePointResponse.builder()
            .date(candles.get(period - 1).getDate())
            .value(sum.divide(periodBd, MC))
            .build());

        // Fenêtre glissante pour les points suivants
        for (int i = period; i < candles.size(); i++) {
            sum = sum.add(candles.get(i).getClose())
                     .subtract(candles.get(i - period).getClose());
            points.add(MovingAveragePointResponse.builder()
                .date(candles.get(i).getDate())
                .value(sum.divide(periodBd, MC))
                .build());
        }

        return points;
    }

    /*
        Calcule l'Exponential Moving Average (EMA) pour une période donnée.

        Algorithme :
          1. Calcule la SMA des `period` premiers close comme valeur d'amorçage.
          2. Applique la formule récursive :
               EMA(i) = close(i) * k + EMA(i-1) * (1 - k)
             où k = 2 / (period + 1) est le facteur de lissage.

        L'EMA donne plus de poids aux prix récents que la SMA, ce qui la rend
        plus réactive aux mouvements de marché.

        Si le nombre de candles est inférieur à la période, retourne une liste vide.

        package-private pour testabilité via Mockito.spy()
    */
    List<MovingAveragePointResponse> computeEma(List<AssetDailyValue> candles, int period) {
        if (candles.size() < period) {
            return List.of();
        }

        List<MovingAveragePointResponse> points = new ArrayList<>();

        // Facteur de lissage : k = 2 / (period + 1)
        BigDecimal k = new BigDecimal(2).divide(new BigDecimal(period + 1), MC);
        BigDecimal oneMinusK = BigDecimal.ONE.subtract(k, MC);

        // Amorçage : SMA des `period` premiers close
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(candles.get(i).getClose());
        }
        BigDecimal ema = sum.divide(new BigDecimal(period), MC);

        // Premier point EMA = SMA d'amorçage
        points.add(MovingAveragePointResponse.builder()
            .date(candles.get(period - 1).getDate())
            .value(ema)
            .build());

        // Formule récursive pour les points suivants
        for (int i = period; i < candles.size(); i++) {
            BigDecimal close = candles.get(i).getClose();
            ema = close.multiply(k, MC).add(ema.multiply(oneMinusK, MC), MC);
            points.add(MovingAveragePointResponse.builder()
                .date(candles.get(i).getDate())
                .value(ema)
                .build());
        }

        return points;
    }
}
