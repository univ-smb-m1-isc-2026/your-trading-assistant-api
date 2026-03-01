package fr.info803.trading_assistant.entity;

/*
    Types d'alertes disponibles dans le système.

    Chaque valeur correspond à une condition de déclenchement différente,
    évaluée par un AlertEvaluator dédié (Strategy Pattern).

    Stocker l'enum comme String en base (EnumType.STRING) :
    - Plus lisible en base de données ("PRICE_THRESHOLD" vs index 0).
    - Résistant aux refactorisations : ajouter une valeur entre deux
      existantes ne décale pas les index (contrairement à EnumType.ORDINAL).

    Pour ajouter un nouveau type d'alerte :
    1. Ajouter une valeur ici (ex: MA_CROSSOVER).
    2. Créer un AlertEvaluator correspondant avec @Component.
    3. Aucune modification d'AlertService nécessaire (Open/Closed Principle).
*/
public enum AlertType {

    // Alerte sur seuil de prix : compare le high/low de la bougie au seuil configuré.
    PRICE_THRESHOLD,

    // Alerte sur seuil de volume journalier : compare le volume de la bougie au seuil.
    VOLUME_THRESHOLD
}
