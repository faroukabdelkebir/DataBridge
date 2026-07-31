package wevioo.se.monitoringms.dto.analytics;

import lombok.Builder;
import lombok.Data;

/**
 * Bandeau d'indicateurs métier de la page Synthèse.
 *
 * <p>Les compteurs portent sur l'année sélectionnée, ou sur tout l'historique
 * si aucun filtre n'est appliqué. Les champs {@code delta*} expriment
 * l'évolution en pourcentage face à l'année précédente et valent {@code null}
 * lorsque celle-ci n'a produit aucune donnée exploitable.</p>
 *
 * <p>Les autres champs nullables restent à {@code null} tant que la colonne
 * source n'a pas été activée dans {@code SCHEMA_SOURCE} : l'extraction
 * enrichie conditionne leur disponibilité.</p>
 *
 * @see wevioo.se.monitoringms.service.AnalyticsService#summary(int)
 */
@Data
@Builder
public class AnalyticsSummaryDto {

    /** Nombre total de clients du référentiel. */
    private long    totalClients;

    /** Âge moyen des clients ; {@code null} tant que la date de naissance n'est pas extraite. */
    private Double  ageMoyen;

    /** Nombre de gestionnaires de portefeuille distincts. */
    private long    nbGestionnaires;

    /** Nombre de virements sur la période retenue. */
    private long    nbVirements;

    /** Évolution des virements face à l'année précédente, en pourcentage. */
    private Double  deltaVirementsPct;

    /** Nombre de factures émises sur la période retenue. */
    private long    nbFactures;

    /** Évolution des factures face à l'année précédente, en pourcentage. */
    private Double  deltaFacturesPct;

    /** Montant total viré ; {@code null} tant que {@code AMOUNT_DEBITED} n'est pas extraite. */
    private Double  montantVirements;

    /** Devise la plus représentée ; {@code null} tant que {@code DEBIT_CURRENCY} n'est pas extraite. */
    private String  devisePrincipale;
}
