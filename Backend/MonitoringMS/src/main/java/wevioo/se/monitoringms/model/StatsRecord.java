package wevioo.se.monitoringms.model;

import lombok.Builder;
import lombok.Data;

/**
 * Ligne de la table {@code stats}, renseignée par le microservice d'extraction
 * à l'issue de chaque chargement de table T24.
 *
 * <p>Sert de base à la réconciliation Oracle ↔ SQL Server : on compare le
 * nombre de lignes comptées côté source au nombre de lignes réellement
 * insérées côté cible.</p>
 *
 * @see wevioo.se.monitoringms.service.ReconciliationService
 */
@Data
@Builder
public class StatsRecord {

    /** Exécution Spring Batch ayant produit ce comptage. */
    private Long   jobExecutionId;

    /** Table SQL Server alimentée : {@code CUSTOMER}, {@code FT_HIS}… */
    private String mssqlTable;

    /** Date métier de l'extraction, au format numérique {@code AAAAMMJJ}. */
    private int    businessDate;

    /** Lignes comptées côté Oracle avant extraction. */
    private int    oracleCount;

    /** Nombre de lots découpés côté Oracle pour la lecture. */
    private int    oracleChunkCount;

    /** Lignes effectivement insérées côté SQL Server. */
    private int    sqlCount;

    /** Horodatage d'insertion de la ligne de statistiques. */
    private long   insertDate;

    /**
     * Taux de réconciliation : part des lignes source effectivement chargées.
     *
     * <p>Une valeur supérieure à 100 % signale des doublons ou un rechargement
     * partiel non purgé côté cible.</p>
     *
     * @return le taux en pourcentage, ou {@code null} si la source est vide
     *         (le rapport n'aurait alors aucun sens)
     */
    public Double getReconciliationRate() {
        if (oracleCount == 0) return null;
        return (double) sqlCount / oracleCount * 100.0;
    }

    /**
     * Indique si l'écart entre source et cible reste dans la tolérance admise.
     *
     * @param tolerancePct écart maximal toléré, en points de pourcentage
     * @return {@code true} si le taux atteint au moins {@code 100 - tolerancePct} ;
     *         {@code false} si la source est vide, le taux étant alors indéterminé
     */
    public boolean isReconciled(double tolerancePct) {
        Double rate = getReconciliationRate();
        return rate != null && rate >= (100.0 - tolerancePct);
    }
}
