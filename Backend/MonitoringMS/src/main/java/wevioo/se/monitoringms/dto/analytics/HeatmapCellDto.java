package wevioo.se.monitoringms.dto.analytics;

import lombok.Builder;
import lombok.Data;

/**
 * Cellule de la heatmap d'activité, croisant le mois et le jour de semaine.
 *
 * <p>Cette vue fonctionne sans extraction enrichie : la date métier est
 * reconstruite depuis l'identifiant T24 du virement ({@code FTyyddd…}).</p>
 *
 * @see wevioo.se.monitoringms.service.AnalyticsService#activityHeatmap(int)
 */
@Data
@Builder
public class HeatmapCellDto {

    /** Mois concerné, de {@code 1} (janvier) à {@code 12} (décembre). */
    private int  mois;

    /** Jour de semaine, de {@code 0} (lundi) à {@code 6} (dimanche). */
    private int  jourSemaine;

    /** Nombre d'opérations dans cette cellule. */
    private long nb;
}
