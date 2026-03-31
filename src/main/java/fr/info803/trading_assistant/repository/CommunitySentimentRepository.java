package fr.info803.trading_assistant.repository;

import fr.info803.trading_assistant.dto.SentimentPollResponse;
import fr.info803.trading_assistant.entity.Account;
import fr.info803.trading_assistant.entity.Asset;
import fr.info803.trading_assistant.entity.CommunitySentiment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CommunitySentimentRepository extends JpaRepository<CommunitySentiment, Long> {

    Optional<CommunitySentiment> findByAccountAndAsset(Account account, Asset asset);

    @Query("""
        SELECT new fr.info803.trading_assistant.dto.SentimentPollResponse(
            a.symbol,
            SUM(CASE WHEN c.type = 'BULLISH' THEN 1 ELSE 0 END),
            SUM(CASE WHEN c.type = 'BEARISH' THEN 1 ELSE 0 END)
        )
        FROM CommunitySentiment c
        JOIN c.asset a
        WHERE a.symbol = :symbol
        GROUP BY a.symbol
    """)
    Optional<SentimentPollResponse> getPollResultsBySymbol(@Param("symbol") String symbol);
}
