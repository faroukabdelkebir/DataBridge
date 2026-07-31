package wevioo.se.monitoringms.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vue d'ensemble du processus d'extraction T24, renvoyée en une seule requête
 * au tableau de bord principal.
 *
 * <p>Agrège compteurs, traitements en cours, historique récent et alertes
 * actives afin d'éviter au front d'enchaîner plusieurs appels. Le contenu est
 * mis en cache trente secondes côté serveur : {@link #generatedAt} permet d'en
 * mesurer la fraîcheur.</p>
 *
 * @see wevioo.se.monitoringms.service.DashboardService
 */
@Data
@Builder
public class DashboardSummaryDto {

    /** Nombre de traitements actuellement en cours d'exécution. */
    private int runningJobsCount;

    /** Nombre de traitements terminés avec succès depuis minuit. */
    private int completedTodayCount;

    /** Nombre de traitements en échec depuis minuit. */
    private int failedTodayCount;

    /** Taux de succès sur les sept derniers jours, en pourcentage. */
    private double successRate7d;

    /** Statut de la clôture journalière T24 (« COB »), déjà traduit pour l'affichage. */
    private String cobStatus;

    /**
     * Date métier active, déduite de la dernière extraction {@code T24} réussie.
     * Vaut « Non disponible » si aucune exécution réussie n'est connue.
     */
    private String lastBusinessDate;

    /** Traitements actuellement actifs. */
    private List<JobExecutionDto> liveJobs;

    /** Dix dernières exécutions, du plus récent au plus ancien. */
    private List<JobExecutionDto> recentExecutions;

    /** Alertes actives non acquittées. */
    private List<AlertDto> activeAlerts;

    /** Instant de construction du résumé, utile pour interpréter les données mises en cache. */
    private LocalDateTime generatedAt;
}
