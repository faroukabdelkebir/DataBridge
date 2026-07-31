package wevioo.se.monitoringms.dto.analytics;

import lombok.Builder;
import lombok.Data;

/**
 * Bandeau d'indicateurs de la page Créances : facturation, encours et
 * recouvrement.
 *
 * <p>Le nombre de factures est toujours disponible car reconstruit depuis
 * l'identifiant T24 ({@code AABILLyyddd…}). Les montants et le suivi des
 * règlements dépendent en revanche de l'extraction enrichie et restent à
 * {@code null} tant que les colonnes correspondantes ne sont pas activées.</p>
 *
 * @see wevioo.se.monitoringms.service.AnalyticsService#billingSummary(int)
 */
@Data
@Builder
public class BillingSummaryDto {

    /** Nombre de factures émises sur la période retenue. */
    private long   nbFactures;

    /** Évolution du nombre de factures face à l'année précédente, en pourcentage. */
    private Double deltaFacturesPct;

    /** Somme des montants facturés ({@code OR_TOTAL_AMOUNT}) ; {@code null} si non extraite. */
    private Double montantFacture;

    /** Somme des encours restant dus ({@code OS_TOTAL_AMOUNT}) ; {@code null} si non extraite. */
    private Double encoursTotal;

    /**
     * Taux de recouvrement, soit {@code 100 × (1 − encours / facturé)},
     * arrondi au dixième. {@code null} si l'un des deux montants manque ou si
     * le montant facturé est nul.
     */
    private Double tauxRecouvrement;

    /**
     * Part des factures réglées, en pourcentage.
     * Déduite de {@code SETTLE_STATUS} ; {@code null} tant qu'il n'est pas extrait.
     */
    private Double pctPayees;

    /**
     * Délai moyen de règlement en jours, compté depuis l'échéance.
     * Une valeur négative signale un règlement anticipé.
     */
    private Double delaiMoyenReglement;
}
