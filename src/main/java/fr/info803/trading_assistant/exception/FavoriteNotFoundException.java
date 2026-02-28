package fr.info803.trading_assistant.exception;

/*
    Levée quand un utilisateur tente de retirer un asset qui n'est pas dans ses favoris.
    Traduite en HTTP 404 Not Found par GlobalExceptionHandler.

    Pourquoi 404 Not Found ?
    - La ressource "favori pour ce symbole" n'existe pas pour cet utilisateur.
    - Un DELETE sur une ressource inexistante retourne 404 (RFC 9110 §9.3.5).
    - Alternative : 204 No Content (DELETE idempotent qui réussit même si absent).
      On préfère 404 ici pour informer le client que quelque chose d'inattendu
      s'est produit (peut indiquer un bug côté client).
*/
public class FavoriteNotFoundException extends RuntimeException {

    private final String symbol;

    public FavoriteNotFoundException(String symbol) {
        super("Asset not in favorites: " + symbol);
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
