package fr.info803.trading_assistant.repository;

import java.util.Map;

import fr.info803.trading_assistant.entity.ChartPatternCategory;
import fr.info803.trading_assistant.entity.ChartPatternType;

/**
 * Interface pour les requêtes personnalisées de ChartPattern.
 */
public interface ChartPatternRepositoryCustom {
    /**
     * Retourne le nombre d'occurrences pour chaque type de figure, avec filtres optionnels.
     */
    Map<ChartPatternType, Long> countByTypeWithFilters(String symbol, ChartPatternCategory category);
}
