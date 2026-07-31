package wevioo.se.monitoringms.dto.analytics;

import lombok.Builder;
import lombok.Data;

/**
 * Bandeau d'indicateurs de la page Clients : démographie et acquisition.
 *
 * <p>Seuls les effectifs et le nombre de gestionnaires sont disponibles sur
 * une extraction non enrichie. Les autres champs restent à {@code null} tant
 * que la colonne source correspondante n'a pas été activée dans
 * {@code SCHEMA_SOURCE}.</p>
 *
 * @see wevioo.se.monitoringms.service.AnalyticsService#customersSummary(int)
 */
@Data
@Builder
public class CustomersSummaryDto {

    /** Nombre total de clients du référentiel. */
    private long   totalClients;

    /** Âge moyen, arrondi au dixième ; {@code null} si la date de naissance n'est pas extraite. */
    private Double ageMoyen;

    /** Nombre de gestionnaires de portefeuille distincts. */
    private long   nbGestionnaires;

    /**
     * Part des dossiers issus de la migration initiale, en pourcentage.
     * Déduite du champ {@code AUTHORISER} ; {@code null} s'il n'est pas extrait.
     */
    private Double pctMigration;

    /**
     * Clients entrés en relation sur l'année sélectionnée.
     * {@code null} tant que {@code DATE_AC_OPND} n'est pas extraite.
     */
    private Long   nouveauxClients;

    /** Évolution des entrées en relation face à l'année précédente, en pourcentage. */
    private Double deltaNouveauxPct;

    /** Part de clientes, en pourcentage ; {@code null} tant que {@code GENDER} n'est pas extraite. */
    private Double pctFemmes;

    /** Nombre de nationalités distinctes ; {@code null} tant que {@code NATIONALITY} n'est pas extraite. */
    private Integer nbNationalites;

    /** Nombre de secteurs d'activité distincts ; {@code null} tant que {@code SECTOR} n'est pas extraite. */
    private Integer nbSecteurs;
}
