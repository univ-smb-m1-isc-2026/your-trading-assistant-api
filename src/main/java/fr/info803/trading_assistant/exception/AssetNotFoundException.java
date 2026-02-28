package fr.info803.trading_assistant.exception;

public class AssetNotFoundException extends RuntimeException {

    private final String symbol;

    public AssetNotFoundException(String symbol) {
        super("Asset not found: " + symbol);
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
