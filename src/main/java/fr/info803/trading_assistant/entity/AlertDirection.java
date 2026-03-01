package fr.info803.trading_assistant.entity;

/*
    Direction de comparaison pour les alertes.

    Détermine le sens de la condition de déclenchement :

    ABOVE : la valeur observée doit être >= au seuil.
      - Pour PRICE_THRESHOLD : compare candle.high >= threshold.
      - Pour VOLUME_THRESHOLD : compare candle.volume >= threshold.
      - Cas d'usage : "Préviens-moi si BTC dépasse 100 000 $".

    BELOW : la valeur observée doit être <= au seuil.
      - Pour PRICE_THRESHOLD : compare candle.low <= threshold.
      - Pour VOLUME_THRESHOLD : compare candle.volume <= threshold.
      - Cas d'usage : "Préviens-moi si ETH descend en dessous de 3 000 $".

    Stocker comme String en base (EnumType.STRING) pour les mêmes raisons
    que AlertType : lisibilité et résistance aux refactorisations.
*/
public enum AlertDirection {
    ABOVE,
    BELOW
}
