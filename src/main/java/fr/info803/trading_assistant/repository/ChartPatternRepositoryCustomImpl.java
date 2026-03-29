package fr.info803.trading_assistant.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import fr.info803.trading_assistant.entity.ChartPattern;
import fr.info803.trading_assistant.entity.ChartPatternCategory;
import fr.info803.trading_assistant.entity.ChartPatternType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Repository
public class ChartPatternRepositoryCustomImpl implements ChartPatternRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Map<ChartPatternType, Long> countByTypeWithFilters(String symbol, ChartPatternCategory category) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<ChartPattern> root = query.from(ChartPattern.class);

        // Sélection: type et count(*)
        query.multiselect(root.get("type"), cb.count(root));
        query.groupBy(root.get("type"));

        // Filtres
        List<Predicate> predicates = new ArrayList<>();

        if (symbol != null && !symbol.isBlank()) {
            String pattern = "%" + symbol.trim().toUpperCase() + "%";
            predicates.add(cb.like(
                cb.upper(root.get("asset").get("symbol")), 
                pattern
            ));
        }

        if (category != null) {
            List<ChartPatternType> typesInCategory = Arrays.stream(ChartPatternType.values())
                .filter(t -> t.getCategory() == category)
                .toList();
            predicates.add(root.get("type").in(typesInCategory));
        }

        if (!predicates.isEmpty()) {
            query.where(cb.and(predicates.toArray(new Predicate[0])));
        }

        List<Tuple> results = entityManager.createQuery(query).getResultList();

        Map<ChartPatternType, Long> stats = new HashMap<>();
        for (Tuple tuple : results) {
            ChartPatternType type = tuple.get(0, ChartPatternType.class);
            Long count = tuple.get(1, Long.class);
            stats.put(type, count);
        }

        return stats;
    }
}
