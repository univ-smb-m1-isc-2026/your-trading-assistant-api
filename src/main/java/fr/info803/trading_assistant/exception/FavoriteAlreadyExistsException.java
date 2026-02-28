package fr.info803.trading_assistant.exception;

/*
    Levée quand un utilisateur tente d'ajouter un asset déjà présent dans ses favoris.
    Traduite en HTTP 409 Conflict par GlobalExceptionHandler.

    Pourquoi 409 Conflict ?
    - La ressource "favori" existe déjà : la requête est en conflit avec l'état actuel
      de la représentation cible (RFC 9110 §15.5.10).
    - Alternative : 422 Unprocessable Content serait aussi défendable, mais 409 est
      plus naturel pour les conflits de doublons (ex : création d'un email déjà pris).
*/
public class FavoriteAlreadyExistsException extends RuntimeException {

    private final String symbol;

    public FavoriteAlreadyExistsException(String symbol) {
        super("Asset already in favorites: " + symbol);
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
