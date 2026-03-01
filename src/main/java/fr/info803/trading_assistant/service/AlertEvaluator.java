package fr.info803.trading_assistant.service;

import java.math.BigDecimal;
import java.util.Optional;

import fr.info803.trading_assistant.entity.Alert;
import fr.info803.trading_assistant.entity.AlertType;
import fr.info803.trading_assistant.entity.AssetDailyValue;

/*
    Interface du Strategy Pattern pour l'évaluation des alertes.

    Même architecture que AssetDataProvider pour les sources de données :
    Spring collecte automatiquement tous les beans implémentant cette interface
    et AlertService les utilise via supports() pour trouver le bon évaluateur.

    Pourquoi le Strategy Pattern ici ?
    - Sans ce pattern, AlertService contiendrait un switch/if-else sur AlertType
      qui grossirait à chaque nouveau type d'alerte.
    - Avec ce pattern : ajouter un nouveau type d'alerte = créer une nouvelle
      classe @Component qui implémente AlertEvaluator. Aucune modification
      d'AlertService nécessaire (Open/Closed Principle).

    Contrat imposé à chaque implémentation :

    1. supports(AlertType type)
       - Retourne true si cet évaluateur gère ce type d'alerte.
       - AlertService parcourt la liste des évaluateurs et délègue au premier
         qui retourne true.

    2. evaluate(Alert alert, AssetDailyValue candle)
       - Évalue la condition de l'alerte contre la bougie quotidienne.
       - Retourne Optional<BigDecimal> :
           * present = la condition est satisfaite, la valeur est celle qui a
             causé le déclenchement (high, low, volume...).
           * empty = la condition n'est pas satisfaite.
       - L'évaluateur ne modifie PAS l'état de l'alerte (active, recurring...).
         C'est AlertService qui gère la logique post-évaluation.
       - Si un futur évaluateur a besoin de plus de données (ex: historique
         de N jours pour un croisement de moyennes mobiles), il injectera
         le repository directement dans son constructeur @Component.
*/
public interface AlertEvaluator {

    boolean supports(AlertType type);

    Optional<BigDecimal> evaluate(Alert alert, AssetDailyValue candle);
}
