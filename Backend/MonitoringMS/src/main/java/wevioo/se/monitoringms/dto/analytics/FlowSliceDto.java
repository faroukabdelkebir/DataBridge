package wevioo.se.monitoringms.dto.analytics;

import lombok.Builder;
import lombok.Data;

/**
 * Tranche d'une répartition de flux, par devise ou par type d'opération.
 *
 * <p>Se distingue de {@link BreakdownSliceDto} par la présence des montants :
 * la part {@link #pct} est calculée sur les montants lorsqu'ils sont
 * disponibles et significatifs, et retombe sur le volume d'opérations sinon.</p>
 *
 * @see wevioo.se.monitoringms.service.AnalyticsService#flowsByCurrency(int)
 */
@Data
@Builder
public class FlowSliceDto {

    /** Valeur de la dimension : code devise, type d'opération, statut… */
    private String label;

    /** Nombre d'opérations de cette tranche. */
    private long   nbOperations;

    /** Montant cumulé ; {@code null} tant que {@code AMOUNT_DEBITED} n'est pas extraite. */
    private Double montantTotal;

    /** Montant moyen par opération ; {@code null} si les montants sont indisponibles. */
    private Double ticketMoyen;

    /**
     * Part de la tranche dans le total, en pourcentage arrondi au dixième.
     * Calculée sur les montants s'ils sont exploitables, sur le volume sinon.
     */
    private double pct;
}
