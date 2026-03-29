package fr.info803.trading_assistant.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.AssetDailyValue;
import fr.info803.trading_assistant.entity.ChartPattern;
import fr.info803.trading_assistant.entity.ChartPatternCategory;
import fr.info803.trading_assistant.entity.ChartPatternType;
import fr.info803.trading_assistant.repository.AssetDailyValueRepository;
import fr.info803.trading_assistant.repository.AssetRepository;
import fr.info803.trading_assistant.repository.ChartPatternRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChartPatternService {

    private final ChartPatternRepository chartPatternRepository;
    private final AssetRepository assetRepository;
    private final AssetDailyValueRepository assetDailyValueRepository;

    public List<fr.info803.trading_assistant.dto.ChartPatternResponse> getPatternsForAsset(String symbol) {
        return chartPatternRepository.findByAssetSymbolOrderByDateDesc(symbol).stream()
            .map(this::mapToResponse)
            .toList();
    }

    public Page<fr.info803.trading_assistant.dto.ChartPatternResponse> getAllPatterns(String symbol, ChartPatternType type, ChartPatternCategory category, Pageable pageable) {
        Specification<ChartPattern> spec = (root, query, cb) -> cb.conjunction();
        if (symbol != null && !symbol.isBlank()) {
            String pattern = "%" + symbol.trim().toUpperCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.upper(root.get("asset").get("symbol")), pattern));
        }
        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        if (category != null) {
            List<ChartPatternType> typesInCategory = Arrays.stream(ChartPatternType.values())
                .filter(t -> t.getCategory() == category)
                .toList();
            spec = spec.and((root, query, cb) -> root.get("type").in(typesInCategory));
        }
        return chartPatternRepository.findAll(spec, pageable)
            .map(this::mapToResponse);
    }

    public Map<ChartPatternType, Long> getStats(String symbol, ChartPatternCategory category) {
        return chartPatternRepository.countByTypeWithFilters(symbol, category);
    }

    public List<fr.info803.trading_assistant.dto.ChartPatternResponse> getPatternsForAssetAndDate(String symbol, LocalDate date) {
        return chartPatternRepository.findByAssetSymbolAndDate(symbol, date).stream()
            .map(this::mapToResponse)
            .toList();
    }

    private fr.info803.trading_assistant.dto.ChartPatternResponse mapToResponse(ChartPattern pattern) {
        return fr.info803.trading_assistant.dto.ChartPatternResponse.builder()
            .id(pattern.getId())
            .assetSymbol(pattern.getAsset().getSymbol())
            .date(pattern.getDate())
            .type(pattern.getType())
            .category(pattern.getType().getCategory())
            .build();
    }

    /**
     * Evalue toutes les figures pour tous les actifs à une date donnée.
     * Cette méthode est conçue pour être appelée après la synchronisation quotidienne.
     */
    @Transactional
    public void evaluatePatterns(LocalDate targetDate) {
        log.info("Evaluating chart patterns for date: {}", targetDate);
        List<Asset> assets = assetRepository.findAll();

        int totalSaved = 0;
        for (Asset asset : assets) {
            // On a besoin des 11 derniers jours pour calculer (ex: la moyenne des 10 jours précédents + le jour actuel)
            List<AssetDailyValue> recentValues = assetDailyValueRepository
                .findTop11ByAssetAndDateLessThanEqualOrderByDateDesc(asset, targetDate);

            // Remettre dans l'ordre chronologique
            recentValues.sort((v1, v2) -> v1.getDate().compareTo(v2.getDate()));

            if (recentValues.isEmpty() || !recentValues.get(recentValues.size() - 1).getDate().equals(targetDate)) {
                // Pas de données pour la date cible pour cet asset
                continue;
            }

            List<ChartPatternType> detectedPatterns = detectPatterns(recentValues);

            for (ChartPatternType type : detectedPatterns) {
                // Check si déjà existant pour éviter les doublons (bien qu'il y ait la contrainte DB)
                boolean exists = chartPatternRepository.findByAssetSymbolAndDate(asset.getSymbol(), targetDate)
                    .stream()
                    .anyMatch(p -> p.getType() == type);

                if (!exists) {
                    ChartPattern pattern = ChartPattern.builder()
                        .asset(asset)
                        .date(targetDate)
                        .type(type)
                        .build();
                    chartPatternRepository.save(pattern);
                    totalSaved++;
                    log.debug("Detected pattern {} on asset {} for date {}", type, asset.getSymbol(), targetDate);
                }
            }
        }
        log.info("Finished evaluating chart patterns. Saved {} patterns.", totalSaved);
    }

    /**
     * Détecte les figures sur la dernière bougie d'une liste donnée.
     * visibility: package-private pour les tests.
     */
    List<ChartPatternType> detectPatterns(List<AssetDailyValue> values) {
        List<ChartPatternType> patterns = new ArrayList<>();
        if (values == null || values.isEmpty()) {
            return patterns;
        }

        int size = values.size();
        AssetDailyValue current = values.get(size - 1);
        AssetDailyValue prev1 = size > 1 ? values.get(size - 2) : null;
        AssetDailyValue prev2 = size > 2 ? values.get(size - 3) : null;

        if (isBullishEngulfing(current, prev1)) patterns.add(ChartPatternType.BULLISH_ENGULFING);
        if (isMorningStar(current, prev1, prev2)) patterns.add(ChartPatternType.MORNING_STAR);
        if (isHammer(current)) patterns.add(ChartPatternType.HAMMER);
        if (isDragonflyDoji(current)) patterns.add(ChartPatternType.DRAGONFLY_DOJI);

        if (isBearishEngulfing(current, prev1)) patterns.add(ChartPatternType.BEARISH_ENGULFING);
        if (isEveningStar(current, prev1, prev2)) patterns.add(ChartPatternType.EVENING_STAR);
        if (isShootingStar(current)) patterns.add(ChartPatternType.SHOOTING_STAR);
        if (isGravestoneDoji(current)) patterns.add(ChartPatternType.GRAVESTONE_DOJI);

        if (isSmallRangedCandle(values)) patterns.add(ChartPatternType.SMALL_RANGED_CANDLE);
        if (isDoji(current)) patterns.add(ChartPatternType.DOJI);
        if (isSmallBodiedCandle(current)) patterns.add(ChartPatternType.SMALL_BODIED_CANDLE);

        return patterns;
    }

    // --- Math helpers ---

    private BigDecimal bodySize(AssetDailyValue v) {
        return v.getOpen().subtract(v.getClose()).abs();
    }

    private BigDecimal totalRange(AssetDailyValue v) {
        return v.getHigh().subtract(v.getLow());
    }

    private BigDecimal maxOpenClose(AssetDailyValue v) {
        return v.getOpen().max(v.getClose());
    }

    private BigDecimal minOpenClose(AssetDailyValue v) {
        return v.getOpen().min(v.getClose());
    }

    private BigDecimal upperWick(AssetDailyValue v) {
        return v.getHigh().subtract(maxOpenClose(v));
    }

    private BigDecimal lowerWick(AssetDailyValue v) {
        return minOpenClose(v).subtract(v.getLow());
    }

    private boolean isRed(AssetDailyValue v) {
        return v.getClose().compareTo(v.getOpen()) < 0;
    }

    private boolean isGreen(AssetDailyValue v) {
        return v.getClose().compareTo(v.getOpen()) > 0;
    }

    // --- Patterns ---

    private boolean isBullishEngulfing(AssetDailyValue current, AssetDailyValue prev1) {
        if (prev1 == null) return false;
        // Bougie 1 rouge, Bougie 2 verte
        if (!isRed(prev1) || !isGreen(current)) return false;
        // Avalement: O2 <= C1 ET C2 >= O1
        return current.getOpen().compareTo(prev1.getClose()) <= 0 &&
               current.getClose().compareTo(prev1.getOpen()) >= 0;
    }

    private boolean isBearishEngulfing(AssetDailyValue current, AssetDailyValue prev1) {
        if (prev1 == null) return false;
        // Bougie 1 verte, Bougie 2 rouge
        if (!isGreen(prev1) || !isRed(current)) return false;
        // Avalement: O2 >= C1 ET C2 <= O1
        return current.getOpen().compareTo(prev1.getClose()) >= 0 &&
               current.getClose().compareTo(prev1.getOpen()) <= 0;
    }

    private boolean isMorningStar(AssetDailyValue current, AssetDailyValue prev1, AssetDailyValue prev2) {
        if (prev1 == null || prev2 == null) return false;
        // prev2 = Bougie 1, prev1 = Bougie 2, current = Bougie 3

        // Bougie 1 (Rouge forte): C1 < O1 et corps assez grand
        if (!isRed(prev2)) return false;
        BigDecimal range1 = totalRange(prev2);
        if (range1.compareTo(BigDecimal.ZERO) == 0) return false;
        if (bodySize(prev2).divide(range1, 4, RoundingMode.HALF_UP).compareTo(new BigDecimal("0.5")) < 0) return false;

        // Bougie 2 (Indécision): corps < 30% de l'amplitude totale
        BigDecimal range2 = totalRange(prev1);
        if (range2.compareTo(BigDecimal.ZERO) == 0) return false;
        if (bodySize(prev1).divide(range2, 4, RoundingMode.HALF_UP).compareTo(new BigDecimal("0.3")) >= 0) return false;

        // Bougie 3 (Verte forte): remonte à la moitié de la bougie 1
        if (!isGreen(current)) return false;
        BigDecimal midpoint1 = prev2.getOpen().add(prev2.getClose()).divide(new BigDecimal("2"), 10, RoundingMode.HALF_UP);
        return current.getClose().compareTo(midpoint1) > 0;
    }

    private boolean isEveningStar(AssetDailyValue current, AssetDailyValue prev1, AssetDailyValue prev2) {
        if (prev1 == null || prev2 == null) return false;
        // prev2 = Bougie 1, prev1 = Bougie 2, current = Bougie 3

        // Bougie 1 (Verte forte)
        if (!isGreen(prev2)) return false;
        BigDecimal range1 = totalRange(prev2);
        if (range1.compareTo(BigDecimal.ZERO) == 0) return false;
        if (bodySize(prev2).divide(range1, 4, RoundingMode.HALF_UP).compareTo(new BigDecimal("0.5")) < 0) return false;

        // Bougie 2 (Indécision)
        BigDecimal range2 = totalRange(prev1);
        if (range2.compareTo(BigDecimal.ZERO) == 0) return false;
        if (bodySize(prev1).divide(range2, 4, RoundingMode.HALF_UP).compareTo(new BigDecimal("0.3")) >= 0) return false;

        // Bougie 3 (Rouge forte): descend à la moitié de la bougie 1
        if (!isRed(current)) return false;
        BigDecimal midpoint1 = prev2.getOpen().add(prev2.getClose()).divide(new BigDecimal("2"), 10, RoundingMode.HALF_UP);
        return current.getClose().compareTo(midpoint1) < 0;
    }

    private boolean isHammer(AssetDailyValue current) {
        BigDecimal range = totalRange(current);
        if (range.compareTo(BigDecimal.ZERO) == 0) return false;
        
        BigDecimal body = bodySize(current);
        BigDecimal lower = lowerWick(current);
        BigDecimal upper = upperWick(current);

        // Mèche basse au moins 2x le corps
        boolean longLowerWick = lower.compareTo(body.multiply(new BigDecimal("2"))) >= 0;
        // Mèche haute quasi inexistante (<= 10% de l'amplitude totale)
        boolean tinyUpperWick = upper.compareTo(range.multiply(new BigDecimal("0.1"))) <= 0;
        // Corps petit, mais existe
        boolean bodyExists = body.compareTo(range.multiply(new BigDecimal("0.05"))) > 0;

        return longLowerWick && tinyUpperWick && bodyExists;
    }

    private boolean isShootingStar(AssetDailyValue current) {
        BigDecimal range = totalRange(current);
        if (range.compareTo(BigDecimal.ZERO) == 0) return false;

        BigDecimal body = bodySize(current);
        BigDecimal lower = lowerWick(current);
        BigDecimal upper = upperWick(current);

        // Mèche haute au moins 2x le corps
        boolean longUpperWick = upper.compareTo(body.multiply(new BigDecimal("2"))) >= 0;
        // Mèche basse quasi inexistante
        boolean tinyLowerWick = lower.compareTo(range.multiply(new BigDecimal("0.1"))) <= 0;
        // Corps petit, mais existe
        boolean bodyExists = body.compareTo(range.multiply(new BigDecimal("0.05"))) > 0;

        return longUpperWick && tinyLowerWick && bodyExists;
    }

    private boolean isDoji(AssetDailyValue current) {
        BigDecimal range = totalRange(current);
        if (range.compareTo(BigDecimal.ZERO) == 0) return true; // Ligne plate
        // Corps <= 5% de l'amplitude
        return bodySize(current).compareTo(range.multiply(new BigDecimal("0.05"))) <= 0;
    }

    private boolean isDragonflyDoji(AssetDailyValue current) {
        if (!isDoji(current)) return false;
        BigDecimal range = totalRange(current);
        if (range.compareTo(BigDecimal.ZERO) == 0) return false;

        // Mèche haute quasi inexistante (O et C très proches de H)
        return upperWick(current).compareTo(range.multiply(new BigDecimal("0.05"))) <= 0;
    }

    private boolean isGravestoneDoji(AssetDailyValue current) {
        if (!isDoji(current)) return false;
        BigDecimal range = totalRange(current);
        if (range.compareTo(BigDecimal.ZERO) == 0) return false;

        // Mèche basse quasi inexistante (O et C très proches de L)
        return lowerWick(current).compareTo(range.multiply(new BigDecimal("0.05"))) <= 0;
    }

    private boolean isSmallBodiedCandle(AssetDailyValue current) {
        if (isDoji(current)) return false; // Exclure si c'est déjà un doji
        BigDecimal range = totalRange(current);
        if (range.compareTo(BigDecimal.ZERO) == 0) return false;

        BigDecimal body = bodySize(current);
        BigDecimal upper = upperWick(current);
        BigDecimal lower = lowerWick(current);

        // Petit corps (ex: <= 30% amplitude)
        boolean smallBody = body.compareTo(range.multiply(new BigDecimal("0.3"))) <= 0;
        
        // Mèches à peu près équivalentes: diff(upper, lower) <= 15% du range
        BigDecimal diffWicks = upper.subtract(lower).abs();
        boolean symmetricWicks = diffWicks.compareTo(range.multiply(new BigDecimal("0.15"))) <= 0;

        return smallBody && symmetricWicks;
    }

    private boolean isSmallRangedCandle(List<AssetDailyValue> values) {
        if (values == null || values.size() < 2) return false;
        
        AssetDailyValue current = values.get(values.size() - 1);
        BigDecimal currentRange = totalRange(current);

        // Calculer la moyenne de l'amplitude des 10 bougies précédentes (ou moins s'il y a < 11 éléments)
        BigDecimal sumRanges = BigDecimal.ZERO;
        int count = 0;
        for (int i = 0; i < values.size() - 1; i++) {
            sumRanges = sumRanges.add(totalRange(values.get(i)));
            count++;
        }
        
        if (count == 0) return false;

        BigDecimal avgRange = sumRanges.divide(new BigDecimal(count), 10, RoundingMode.HALF_UP);
        if (avgRange.compareTo(BigDecimal.ZERO) == 0) return false;

        // "Amplitude très faible par rapport aux bougies précédentes" (ex: < 60% de la moyenne)
        return currentRange.compareTo(avgRange.multiply(new BigDecimal("0.6"))) < 0;
    }
}
