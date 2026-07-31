package wevioo.se.monitoringms.dto.analytics;

import lombok.Builder;
import lombok.Data;

/**
 * Portefeuille d'un gestionnaire de clientèle, identifié par le champ T24
 * {@code ACCOUNT_OFFICER}.
 *
 * @see wevioo.se.monitoringms.service.AnalyticsService#topOfficers(int)
 */
@Data
@Builder
public class OfficerPerfDto {

    /** Code du gestionnaire dans T24. */
    private String officer;

    /** Nombre de clients rattachés à ce gestionnaire. */
    private long   nbClients;

    /** Âge moyen de son portefeuille ; {@code null} si la date de naissance n'est pas extraite. */
    private Double ageMoyen;

    /** Part du portefeuille total, en pourcentage arrondi au dixième. */
    private double partPct;
}
