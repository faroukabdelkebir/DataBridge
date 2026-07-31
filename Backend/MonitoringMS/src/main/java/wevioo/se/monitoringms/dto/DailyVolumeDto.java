package wevioo.se.monitoringms.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Volumétrie d'une journée d'extraction, utilisée pour tracer la tendance
 * du tableau de bord.
 *
 * @see KpiDto
 */
@Data
@Builder
public class DailyVolumeDto {

    /** Journée concernée, au format compact {@code AAAAMMJJ}. */
    private String date;

    /** Lignes lues depuis Oracle ce jour-là, toutes étapes confondues. */
    private long   rowsRead;

    /** Lignes écrites en SQL Server ce jour-là, toutes étapes confondues. */
    private long   rowsWritten;

    /** Nombre d'exécutions distinctes lancées ce jour-là. */
    private int    jobCount;

    /** Part des exécutions terminées avec succès ce jour-là, en pourcentage. */
    private double successRate;
}
