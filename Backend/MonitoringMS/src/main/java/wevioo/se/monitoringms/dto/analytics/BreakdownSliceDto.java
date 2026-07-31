package wevioo.se.monitoringms.dto.analytics;

import lombok.Builder;
import lombok.Data;

/**
 * Tranche d'une répartition en volume : pyramide des âges, générations,
 * secteurs d'activité, vieillissement des créances…
 *
 * <p>Forme simplifiée de {@link FlowSliceDto}, sans montant : la part est
 * toujours calculée sur les effectifs.</p>
 *
 * @see wevioo.se.monitoringms.service.AnalyticsService#agePyramid()
 */
@Data
@Builder
public class BreakdownSliceDto {

    /** Valeur de la dimension : tranche d'âge, génération, code secteur… */
    private String label;

    /** Effectif de la tranche. */
    private long   value;

    /** Part de la tranche dans le total, en pourcentage arrondi au dixième. */
    private double pct;
}
