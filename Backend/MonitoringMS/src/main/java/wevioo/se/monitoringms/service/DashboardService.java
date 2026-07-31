package wevioo.se.monitoringms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import wevioo.se.monitoringms.config.CacheConfig;
import wevioo.se.monitoringms.dto.DashboardSummaryDto;
import wevioo.se.monitoringms.dto.JobExecutionDto;
import wevioo.se.monitoringms.model.BatchJobExecution;
import wevioo.se.monitoringms.repository.BatchExecutionRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service du tableau de bord principal : agrège en une seule réponse les
 * données de la vue d'ensemble.
 *
 * <p>Regroupe compteurs, traitements en cours, historique récent et alertes
 * actives afin d'épargner au front une série d'appels. Les deux méthodes
 * exposées sont mises en cache, avec des durées différentes selon la fraîcheur
 * attendue.</p>
 *
 * @see wevioo.se.monitoringms.config.CacheConfig
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    /** Accès aux métadonnées Spring Batch. */
    private final BatchExecutionRepository batchRepo;

    /** Service d'indicateurs, réservé aux évolutions futures du résumé. */
    private final KpiService               kpiService;

    /** Source des alertes actives affichées sur le tableau de bord. */
    private final AlertService             alertService;

    /** Traduction des statuts techniques, réservée aux évolutions futures. */
    private final DisplayTranslationService displayTranslationService;

    /**
     * Construit le résumé complet du tableau de bord.
     *
     * <p>Le résultat est mis en cache trente secondes : le tableau de bord se
     * rafraîchit plus souvent que cela, et servir la version mémorisée évite
     * de multiplier les agrégats sur la base.</p>
     *
     * @return la vue d'ensemble, horodatée par son instant de construction
     */
    @Cacheable(value = CacheConfig.CACHE_DASHBOARD, key = "'summary'")
    public DashboardSummaryDto getSummary() {
        log.debug("Construction du résumé du tableau de bord...");

        List<BatchJobExecution> running = batchRepo.findRunning();
        List<BatchJobExecution> recent  = batchRepo.findRecent(10);

        return DashboardSummaryDto.builder()
                .runningJobsCount(running.size())
                .completedTodayCount(batchRepo.countCompletedToday())
                .failedTodayCount(batchRepo.countFailedToday())
                .successRate7d(batchRepo.computeSuccessRate(7))
                .cobStatus(resolveCobStatus())
                .lastBusinessDate(resolveLastBusinessDate())
                .liveJobs(toDto(running))
                .recentExecutions(toDto(recent))
                .activeAlerts(alertService.getActiveAlerts())
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Retourne les traitements actuellement en cours.
     *
     * <p>Cache volontairement court — vingt secondes — car cette liste
     * alimente le flux temps réel du tableau de bord.</p>
     *
     * @return les exécutions actives ; liste vide si aucun traitement ne tourne
     */
    @Cacheable(value = CacheConfig.CACHE_LIVE_JOBS, key = "'live'")
    public List<JobExecutionDto> getLiveJobs() {
        return toDto(batchRepo.findRunning());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Détermine l'état de la clôture journalière T24.
     *
     * <p>Implémentation simplifiée retournant une valeur fixe : la logique
     * complète suppose de lire {@code F_TSA_SERVICE.SERVICE_CONTROL}, non
     * encore extrait vers la base décisionnelle.</p>
     *
     * @return le libellé d'état, ou « Inconnu » si l'état n'a pu être déterminé
     */
    private String resolveCobStatus() {
        // On cherche dans les steps récents si le step COB_STATUS a passé
        // Valeurs : "STOP" (COB ok), "COB_RUNNING" (COB actif), "UNKNOWN"
        try {
            // On lit le dernier step COB_STATUS pour connaître l'état
            // (simplifié : la logique complète lit F_TSA_SERVICE.SERVICE_CONTROL)
            return "Arrêté"; // défaut
        } catch (Exception e) {
            log.warn("Impossible de déterminer le statut COB : {}", e.getMessage());
            return "Inconnu";
        }
    }

    /**
     * Déduit la date métier active de la dernière extraction {@code T24} réussie.
     *
     * @return la date au format ISO, ou « Non disponible » si aucune exécution
     *         réussie n'est connue ou si sa date de fin n'est pas renseignée
     */
    private String resolveLastBusinessDate() {
        return batchRepo.findLastSuccessfulByJobName("T24")
                .map(je -> je.getEndTime() != null
                        ? je.getEndTime().toLocalDate().toString()
                        : "Non disponible")
                .orElse("Non disponible");
    }

    /**
     * Convertit des exécutions en DTO, sans agrégat d'étape.
     *
     * <p>Les compteurs de lignes restent à zéro : les renseigner imposerait
     * une requête par exécution, coût inacceptable pour une vue d'ensemble.
     * Le détail est disponible via {@link JobService#getById(long)}.</p>
     *
     * @param executions exécutions à convertir
     * @return les DTO correspondants, dans le même ordre
     */
    private List<JobExecutionDto> toDto(List<BatchJobExecution> executions) {
        return executions.stream()
                .map(je -> JobExecutionDto.builder()
                        .jobExecutionId(je.getJobExecutionId())
                        .jobInstanceId(je.getJobInstanceId())
                        .jobName(je.getJobName())
                        .status(je.getStatus())
                        .exitCode(je.getExitCode())
                        .exitMessage(je.getExitMessage())
                        .startTime(je.getStartTime())
                        .endTime(je.getEndTime())
                        .createTime(je.getCreateTime())
                        .durationSeconds(je.getDurationSeconds())
                        .running(je.isRunning())
                        .failed(je.isFailed())
                        .build())
                .toList();
    }
}

