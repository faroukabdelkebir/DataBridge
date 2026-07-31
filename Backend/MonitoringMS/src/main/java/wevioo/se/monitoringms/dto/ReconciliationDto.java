package wevioo.se.monitoringms.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Résultat de réconciliation Oracle ↔ SQL Server pour une table extraite.
 *
 * <p>Compare le nombre de lignes comptées côté source au nombre de lignes
 * réellement insérées côté cible et qualifie l'écart selon la tolérance
 * configurée.</p>
 *
 * @see wevioo.se.monitoringms.service.ReconciliationService
 */
@Data
@Builder
public class ReconciliationDto {

    /** Table SQL Server concernée. */
    private String  tableName;

    /** Date métier de l'extraction, au format numérique {@code AAAAMMJJ}. */
    private int     businessDate;

    /** Lignes comptées côté Oracle avant extraction. */
    private int     oracleCount;

    /** Lignes effectivement insérées côté SQL Server. */
    private int     sqlServerCount;

    /** Écart {@code oracleCount - sqlServerCount} ; négatif en cas de doublons côté cible. */
    private int     delta;

    /** Part des lignes source correctement chargées, en pourcentage arrondi au centième. */
    private double  reconciliationRate;

    /** Indique si l'écart reste dans la tolérance configurée. */
    private boolean reconciled;

    /** Qualification de l'écart : « Conforme », « Avertissement » ou « Non conforme ». */
    private String  status;
}
