package fr.info803.trading_assistant.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import fr.info803.trading_assistant.dto.PredictionFeaturesDto;
import fr.info803.trading_assistant.entity.AssetDailyValue;

@Service
public class PredictionFeatureService {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    public PredictionFeaturesDto calculateForLatestCandle(List<AssetDailyValue> candles) {
        if (candles == null || candles.isEmpty()) {
            throw new IllegalArgumentException("Candles must not be null or empty");
        }

        List<AssetDailyValue> orderedCandles = candles.stream()
            .sorted(Comparator.comparing(AssetDailyValue::getDate))
            .toList();

        int targetIndex = orderedCandles.size() - 1;
        ensureMinimumHistory(targetIndex);

        BigDecimal return1d = computeReturn(orderedCandles, targetIndex, 1);
        BigDecimal return2d = computeReturn(orderedCandles, targetIndex, 2);
        BigDecimal return3d = computeReturn(orderedCandles, targetIndex, 3);
        BigDecimal return5d = computeReturn(orderedCandles, targetIndex, 5);
        BigDecimal return10d = computeReturn(orderedCandles, targetIndex, 10);
        BigDecimal return20d = computeReturn(orderedCandles, targetIndex, 20);

        BigDecimal closeVsMa5 = computeCloseVsSma(orderedCandles, targetIndex, 5);
        BigDecimal closeVsMa10 = computeCloseVsSma(orderedCandles, targetIndex, 10);
        BigDecimal closeVsMa20 = computeCloseVsSma(orderedCandles, targetIndex, 20);
        BigDecimal closeVsMa50 = computeCloseVsSma(orderedCandles, targetIndex, 50);

        BigDecimal volatility5 = computeReturnVolatility(orderedCandles, targetIndex, 5);
        BigDecimal volatility10 = computeReturnVolatility(orderedCandles, targetIndex, 10);
        BigDecimal volatility20 = computeReturnVolatility(orderedCandles, targetIndex, 20);

        BigDecimal volumeRatio5 = computeVolumeRatio(orderedCandles, targetIndex, 5);
        BigDecimal volumeRatio20 = computeVolumeRatio(orderedCandles, targetIndex, 20);

        BigDecimal highLowRange = computeHighLowRange(orderedCandles.get(targetIndex));
        BigDecimal openGap = computeOpenGap(orderedCandles, targetIndex);

        BigDecimal rsi14 = computeRsi(orderedCandles, targetIndex, 14);
        BigDecimal macdSignalDiff = computeMacdSignalDiff(orderedCandles, targetIndex);
        BigDecimal bollingerPos = computeBollingerPosition(orderedCandles, targetIndex, 20);
        BigDecimal atr14Pct = computeAtrPct(orderedCandles, targetIndex, 14);

        LocalDate targetDate = orderedCandles.get(targetIndex).getDate();

        return new PredictionFeaturesDto(
            return1d,
            return2d,
            return3d,
            return5d,
            return10d,
            return20d,
            closeVsMa5,
            closeVsMa10,
            closeVsMa20,
            closeVsMa50,
            volatility5,
            volatility10,
            volatility20,
            volumeRatio5,
            volumeRatio20,
            highLowRange,
            openGap,
            rsi14,
            macdSignalDiff,
            bollingerPos,
            atr14Pct,
            targetDate.getDayOfWeek().getValue()
        );
    }

    private void ensureMinimumHistory(int targetIndex) {
        int minimumIndex = 49;
        if (targetIndex < minimumIndex) {
            throw new IllegalArgumentException("At least 50 candles are required to compute prediction features");
        }
    }

    private BigDecimal computeReturn(List<AssetDailyValue> candles, int targetIndex, int lookbackDays) {
        BigDecimal closeToday = candles.get(targetIndex).getClose();
        BigDecimal closePast = candles.get(targetIndex - lookbackDays).getClose();
        if (closePast.compareTo(ZERO) == 0) {
            return ZERO;
        }
        return closeToday.divide(closePast, MC).subtract(ONE, MC);
    }

    private BigDecimal computeCloseVsSma(List<AssetDailyValue> candles, int targetIndex, int period) {
        BigDecimal sma = computeSma(candles, targetIndex, period, ValueType.CLOSE);
        if (sma.compareTo(ZERO) == 0) {
            return ZERO;
        }
        BigDecimal close = candles.get(targetIndex).getClose();
        return close.divide(sma, MC).subtract(ONE, MC);
    }

    private BigDecimal computeReturnVolatility(List<AssetDailyValue> candles, int targetIndex, int period) {
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = targetIndex - period + 1; i <= targetIndex; i++) {
            BigDecimal closeToday = candles.get(i).getClose();
            BigDecimal closeYesterday = candles.get(i - 1).getClose();
            if (closeYesterday.compareTo(ZERO) == 0) {
                returns.add(ZERO);
            } else {
                returns.add(closeToday.divide(closeYesterday, MC).subtract(ONE, MC));
            }
        }
        return computePopulationStdDev(returns);
    }

    private BigDecimal computeVolumeRatio(List<AssetDailyValue> candles, int targetIndex, int period) {
        BigDecimal smaVolume = computeSma(candles, targetIndex, period, ValueType.VOLUME);
        if (smaVolume.compareTo(ZERO) == 0) {
            return ZERO;
        }
        BigDecimal volumeToday = candles.get(targetIndex).getVolume();
        return volumeToday.divide(smaVolume, MC);
    }

    private BigDecimal computeHighLowRange(AssetDailyValue candle) {
        BigDecimal close = candle.getClose();
        if (close.compareTo(ZERO) == 0) {
            return ZERO;
        }
        BigDecimal range = candle.getHigh().subtract(candle.getLow(), MC);
        return range.divide(close, MC);
    }

    private BigDecimal computeOpenGap(List<AssetDailyValue> candles, int targetIndex) {
        BigDecimal previousClose = candles.get(targetIndex - 1).getClose();
        if (previousClose.compareTo(ZERO) == 0) {
            return ZERO;
        }
        BigDecimal openToday = candles.get(targetIndex).getOpen();
        return openToday.subtract(previousClose, MC).divide(previousClose, MC);
    }

    private BigDecimal computeRsi(List<AssetDailyValue> candles, int targetIndex, int period) {
        BigDecimal sumGain = ZERO;
        BigDecimal sumLoss = ZERO;

        for (int i = 1; i <= period; i++) {
            BigDecimal delta = candles.get(i).getClose().subtract(candles.get(i - 1).getClose(), MC);
            if (delta.compareTo(ZERO) > 0) {
                sumGain = sumGain.add(delta, MC);
            } else {
                sumLoss = sumLoss.add(delta.abs(), MC);
            }
        }

        BigDecimal periodBd = BigDecimal.valueOf(period);
        BigDecimal avgGain = sumGain.divide(periodBd, MC);
        BigDecimal avgLoss = sumLoss.divide(periodBd, MC);

        for (int i = period + 1; i <= targetIndex; i++) {
            BigDecimal delta = candles.get(i).getClose().subtract(candles.get(i - 1).getClose(), MC);
            BigDecimal gain = delta.compareTo(ZERO) > 0 ? delta : ZERO;
            BigDecimal loss = delta.compareTo(ZERO) < 0 ? delta.abs() : ZERO;

            avgGain = avgGain.multiply(BigDecimal.valueOf(period - 1), MC)
                .add(gain, MC)
                .divide(periodBd, MC);
            avgLoss = avgLoss.multiply(BigDecimal.valueOf(period - 1), MC)
                .add(loss, MC)
                .divide(periodBd, MC);
        }

        return toRsi(avgGain, avgLoss);
    }

    private BigDecimal toRsi(BigDecimal avgGain, BigDecimal avgLoss) {
        if (avgGain.compareTo(ZERO) == 0 && avgLoss.compareTo(ZERO) == 0) {
            return new BigDecimal("50");
        }
        if (avgLoss.compareTo(ZERO) == 0) {
            return new BigDecimal("100");
        }
        if (avgGain.compareTo(ZERO) == 0) {
            return ZERO;
        }

        BigDecimal rs = avgGain.divide(avgLoss, MC);
        return new BigDecimal("100").subtract(
            new BigDecimal("100").divide(ONE.add(rs, MC), MC),
            MC
        );
    }

    private BigDecimal computeMacdSignalDiff(List<AssetDailyValue> candles, int targetIndex) {
        List<BigDecimal> closes = candles.stream().map(AssetDailyValue::getClose).toList();
        List<BigDecimal> ema12 = computeEmaSeries(closes, 12);
        List<BigDecimal> ema26 = computeEmaSeries(closes, 26);

        List<BigDecimal> macd = new ArrayList<>();
        List<Integer> macdIndexes = new ArrayList<>();

        for (int i = 0; i <= targetIndex; i++) {
            BigDecimal shortEma = ema12.get(i);
            BigDecimal longEma = ema26.get(i);
            if (shortEma != null && longEma != null) {
                macd.add(shortEma.subtract(longEma, MC));
                macdIndexes.add(i);
            }
        }

        List<BigDecimal> signalSeries = computeEmaSeries(macd, 9);
        BigDecimal macdAtTarget = null;
        BigDecimal signalAtTarget = null;

        for (int i = 0; i < macdIndexes.size(); i++) {
            int originalIndex = macdIndexes.get(i);
            if (originalIndex == targetIndex) {
                macdAtTarget = macd.get(i);
                signalAtTarget = signalSeries.get(i);
                break;
            }
        }

        if (macdAtTarget == null || signalAtTarget == null) {
            return ZERO;
        }

        return macdAtTarget.subtract(signalAtTarget, MC);
    }

    private BigDecimal computeBollingerPosition(List<AssetDailyValue> candles, int targetIndex, int period) {
        List<BigDecimal> closes = new ArrayList<>();
        for (int i = targetIndex - period + 1; i <= targetIndex; i++) {
            closes.add(candles.get(i).getClose());
        }

        BigDecimal mean = average(closes);
        BigDecimal std = computePopulationStdDev(closes);
        if (std.compareTo(ZERO) == 0) {
            return new BigDecimal("0.5");
        }

        BigDecimal upperBand = mean.add(std.multiply(new BigDecimal("2"), MC), MC);
        BigDecimal lowerBand = mean.subtract(std.multiply(new BigDecimal("2"), MC), MC);
        BigDecimal denominator = upperBand.subtract(lowerBand, MC);
        if (denominator.compareTo(ZERO) == 0) {
            return new BigDecimal("0.5");
        }

        BigDecimal close = candles.get(targetIndex).getClose();
        return close.subtract(lowerBand, MC).divide(denominator, MC);
    }

    private BigDecimal computeAtrPct(List<AssetDailyValue> candles, int targetIndex, int period) {
        BigDecimal atr = computeAtr(candles, targetIndex, period);
        BigDecimal close = candles.get(targetIndex).getClose();
        if (close.compareTo(ZERO) == 0) {
            return ZERO;
        }
        return atr.divide(close, MC);
    }

    private BigDecimal computeAtr(List<AssetDailyValue> candles, int targetIndex, int period) {
        List<BigDecimal> trueRanges = new ArrayList<>();
        for (int i = 1; i <= targetIndex; i++) {
            trueRanges.add(computeTrueRange(candles.get(i), candles.get(i - 1)));
        }

        BigDecimal periodBd = BigDecimal.valueOf(period);
        BigDecimal initialAtr = average(trueRanges.subList(0, period));
        BigDecimal atr = initialAtr;

        for (int i = period; i < trueRanges.size(); i++) {
            BigDecimal tr = trueRanges.get(i);
            atr = atr.multiply(BigDecimal.valueOf(period - 1), MC)
                .add(tr, MC)
                .divide(periodBd, MC);
        }
        return atr;
    }

    private BigDecimal computeTrueRange(AssetDailyValue current, AssetDailyValue previous) {
        BigDecimal highLow = current.getHigh().subtract(current.getLow(), MC).abs();
        BigDecimal highPrevClose = current.getHigh().subtract(previous.getClose(), MC).abs();
        BigDecimal lowPrevClose = current.getLow().subtract(previous.getClose(), MC).abs();
        return highLow.max(highPrevClose).max(lowPrevClose);
    }

    private BigDecimal computeSma(List<AssetDailyValue> candles, int targetIndex, int period, ValueType valueType) {
        BigDecimal sum = ZERO;
        for (int i = targetIndex - period + 1; i <= targetIndex; i++) {
            if (valueType == ValueType.CLOSE) {
                sum = sum.add(candles.get(i).getClose(), MC);
            } else {
                sum = sum.add(candles.get(i).getVolume(), MC);
            }
        }
        return sum.divide(BigDecimal.valueOf(period), MC);
    }

    private BigDecimal average(List<BigDecimal> values) {
        BigDecimal sum = ZERO;
        for (BigDecimal value : values) {
            sum = sum.add(value, MC);
        }
        return sum.divide(BigDecimal.valueOf(values.size()), MC);
    }

    private BigDecimal computePopulationStdDev(List<BigDecimal> values) {
        BigDecimal mean = average(values);
        BigDecimal varianceSum = ZERO;
        for (BigDecimal value : values) {
            BigDecimal diff = value.subtract(mean, MC);
            varianceSum = varianceSum.add(diff.multiply(diff, MC), MC);
        }
        BigDecimal variance = varianceSum.divide(BigDecimal.valueOf(values.size()), MC);
        return variance.sqrt(MC);
    }

    private List<BigDecimal> computeEmaSeries(List<BigDecimal> values, int period) {
        List<BigDecimal> series = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            series.add(null);
        }

        if (values.size() < period) {
            return series;
        }

        BigDecimal sum = ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(values.get(i), MC);
        }

        BigDecimal ema = sum.divide(BigDecimal.valueOf(period), MC);
        series.set(period - 1, ema);

        BigDecimal k = new BigDecimal("2").divide(BigDecimal.valueOf(period + 1L), MC);
        BigDecimal oneMinusK = ONE.subtract(k, MC);
        for (int i = period; i < values.size(); i++) {
            ema = values.get(i).multiply(k, MC).add(ema.multiply(oneMinusK, MC), MC);
            series.set(i, ema);
        }

        return series;
    }

    private enum ValueType {
        CLOSE,
        VOLUME
    }
}
