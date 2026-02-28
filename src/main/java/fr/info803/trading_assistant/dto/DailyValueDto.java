package fr.info803.trading_assistant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

/*
    DTO générique représentant une valeur OHLCV journalière.

    Rôle dans l'architecture :
      - C'est l'objet de transfert neutre entre les providers (couche d'accès aux API
        externes) et le service de synchronisation (couche métier).
      - Chaque AssetDataProvider transforme la réponse brute de son API dans ce format commun.
      - AssetDataSyncService ne connaît que DailyValueDto, jamais les formats Hyperliquid
        ou autres — c'est le principe d'isolation des responsabilités.

    Pourquoi @Getter sans @Setter ?
      - Un DTO en sortie d'un provider doit être immuable une fois construit.
      - @Builder permet de construire l'objet de manière lisible sans constructeur verbeux.
      - Pas de @Setter : on ne modifie jamais un DTO après construction.
*/
@Getter
@Builder
public class DailyValueDto {

    // Date de la bougie (ex: 2025-01-15). Convertie depuis le timestamp API par le provider.
    private LocalDate date;

    // Prix d'ouverture de la journée.
    private BigDecimal open;

    // Prix le plus haut de la journée.
    private BigDecimal high;

    // Prix le plus bas de la journée.
    private BigDecimal low;

    // Prix de clôture de la journée.
    private BigDecimal close;

    // Volume total échangé (en unités de l'asset).
    private BigDecimal volume;
}
