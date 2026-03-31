package fr.info803.trading_assistant.dto;

public record SentimentPollResponse(
        String symbol,
        long bullishCount,
        long bearishCount,
        long totalVotes,
        double bullishPercentage
) {
    public SentimentPollResponse(String symbol, long bullishCount, long bearishCount) {
        this(
                symbol,
                bullishCount,
                bearishCount,
                bullishCount + bearishCount,
                (bullishCount + bearishCount) == 0 ? 0.0 : (double) bullishCount / (bullishCount + bearishCount) * 100.0
        );
    }
}
