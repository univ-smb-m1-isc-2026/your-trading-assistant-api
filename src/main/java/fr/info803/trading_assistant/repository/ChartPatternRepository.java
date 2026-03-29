package fr.info803.trading_assistant.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import fr.info803.trading_assistant.entity.ChartPattern;

@Repository
public interface ChartPatternRepository extends JpaRepository<ChartPattern, Long>, JpaSpecificationExecutor<ChartPattern>, ChartPatternRepositoryCustom {
    Page<ChartPattern> findAll(Specification<ChartPattern> spec, Pageable pageable);
    Page<ChartPattern> findAllByOrderByDateDesc(Pageable pageable);
    List<ChartPattern> findAllByOrderByDateDesc();
    List<ChartPattern> findByAssetSymbolOrderByDateDesc(String symbol);
    List<ChartPattern> findByAssetSymbolAndDate(String symbol, LocalDate date);
}
