package wevioo.se.monitoringms.dto.analytics;

import lombok.Builder;
import lombok.Data;

/**
 * Bandeau d'indicateurs de la page Flux : virements en volume et en montant.
 *
 * <p>Les volumes sont toujours disponibles car reconstruits depuis
 * l'identifiant T24 ; les montants et la ventilation par devise dépendent en
 * revanche de l'extraction enrichie et restent {@code null} tant que les
 * colonnes correspondantes ne sont pas activées.</p>
 *
 * @see wevioo.se.monitoringms.service.AnalyticsService#flowsSummary(int)
 */
@Data
@Builder
public class FlowsSummaryDto {

    /** Nombre d'opérations sur la période retenue. */
    private long    nbOperations;

    /** Évolution du nombre d'opérations face à l'année précédente, en pourcentage. */
    private Double  deltaOpsPct;

    /** Montant total viré ; {@code null} tant que {@code AMOUNT_DEBITED} n'est pas extraite. */
    private Double  montantTotal;

    /** Évolution du montant face à l'année précédente, en pourcentage. */
    private Double  deltaMontantPct;

    /** Montant moyen par opération ; {@code null} si les montants sont indisponibles. */
    private Double  ticketMoyen;

    /** Devise la plus représentée ; {@code null} tant que {@code DEBIT_CURRENCY} n'est pas extraite. */
    private String  deviseDominante;

    /** Nombre de devises distinctes observées ; {@code null} si la colonne n'est pas extraite. */
    private Integer nbDevises;

    /** Nombre de types d'opération distincts ; {@code null} tant que {@code TRANSACTION_TYPE} n'est pas extraite. */
    private Integer nbTypes;
}
