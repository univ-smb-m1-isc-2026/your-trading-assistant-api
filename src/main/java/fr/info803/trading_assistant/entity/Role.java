package fr.info803.trading_assistant.entity;

/*
    Enumération des rôles disponibles dans l'application.

    Spring Security utilise le préfixe "ROLE_" par convention pour identifier les rôles.
    Lorsque vous utilisez hasRole("USER"), Spring cherche automatiquement "ROLE_USER".
    Lorsque vous utilisez hasAuthority("ROLE_USER"), vous spécifiez la valeur complète.

    Stocker l'enum comme String en base (EnumType.STRING) est recommandé car :
    - Plus lisible en base de données
    - Résistant aux refactorisations (vs EnumType.ORDINAL qui stocke l'index)
*/
public enum Role {
    ROLE_USER,
    ROLE_ADMIN
}
