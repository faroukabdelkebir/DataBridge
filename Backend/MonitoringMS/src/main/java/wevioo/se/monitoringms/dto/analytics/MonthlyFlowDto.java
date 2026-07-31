package wevioo.se.monitoringms.dto.analytics;

import lombok.Builder;
import lombok.Data;

/**
 * Point mensuel d'une série temporelle, comparant l'année sélectionnée à la
 * précédente.
 *
 * <p>Les séries produites par le service comptent systématiquement douze
 * points, les mois sans donnée étant remplis à zéro : le front peut ainsi
 * tracer la courbe sans traiter les trous.</p>
 *
 * @see wevioo.se.monitoringms.service.AnalyticsService#monthlyTransfers(int)
 */
@Data
@Builder
public class MonthlyFlowDto {

    /** Mois concerné, de {@code 1} (janvier) à {@code 12} (décembre). */
    private int    mois;

    /** Nombre d'opérations sur l'année sélectionnée. */
    private long   nbOperations;

    /**
     * Nombre d'opérations du même mois l'année précédente.
     * {@code null} lorsqu'aucun filtre d'année n'est appliqué, la comparaison
     * n'ayant alors pas de sens.
     */
    private Long   nbOperationsN1;

    /** Montant cumulé du mois ; {@code null} tant que les montants ne sont pas extraits. */
    private Double montantTotal;

    /** Montant cumulé du même mois l'année précédente. */
    private Double montantTotalN1;
}
