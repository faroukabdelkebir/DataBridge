package wevioo.se.monitoringms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import wevioo.se.monitoringms.dto.JobExecutionDto;
import wevioo.se.monitoringms.dto.ReconciliationDto;
import wevioo.se.monitoringms.dto.StepExecutionDto;
import wevioo.se.monitoringms.model.BatchJobExecution;
import wevioo.se.monitoringms.model.BatchStepExecution;
import wevioo.se.monitoringms.repository.BatchExecutionRepository;
import wevioo.se.monitoringms.repository.StatsRepository;

import java.util.List;

/**
 * Navigation dans l'historique des traitements, du résumé jusqu'au détail des
 * étapes.
 *
 * <p>Les méthodes de liste diffèrent par la présence des agrégats d'étapes :
 * les enrichir suppose une requête supplémentaire par exécution, coût acceptable
 * sur une liste courte mais pas sur un historique de plusieurs jours.
 * {@link #getByDays(int)} s'en dispense donc.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    /** Accès aux métadonnées Spring Batch. */
    private final BatchExecutionRepository batchRepo;

    /** Accès aux comptages d'extraction, pour la réconciliation. */
    private final StatsRepository statsRepo;

    /** Traduction des statuts techniques, réservée aux évolutions futures. */
    private final DisplayTranslationService displayTranslationService;

    // ── Liste ────────────────────────────────────────────────────────────────

    /**
     * Retourne les dernières exécutions, agrégats d'étapes compris.
     *
     * @param limit nombre maximal d'exécutions à ramener
     * @return les exécutions les plus récentes, du plus récent au plus ancien
     */
    public List<JobExecutionDto> getRecent(int limit) {
        return batchRepo.findRecent(limit).stream()
                .map(je -> enrich(je, true))
                .toList();
    }

    /**
     * Retourne les exécutions d'une fenêtre glissante.
     *
     * <p>Les agrégats d'étapes sont omis — compteurs à zéro et débit
     * {@code null} — afin d'éviter une requête par exécution sur un historique
     * potentiellement long.</p>
     *
     * @param days profondeur de la fenêtre, en jours
     * @return les exécutions de la période, du plus récent au plus ancien
     */
    public List<JobExecutionDto> getByDays(int days) {
        return batchRepo.findByDays(days).stream()
                .map(je -> enrich(je, false))
                .toList();
    }

    /**
     * Retourne l'historique d'un traitement donné, agrégats compris.
     *
     * @param jobName nom logique du traitement
     * @param limit   nombre maximal d'exécutions à ramener
     * @return les exécutions de ce traitement ; liste vide si le nom est inconnu
     */
    public List<JobExecutionDto> getByJobName(String jobName, int limit) {
        return batchRepo.findByJobName(jobName, limit).stream()
                .map(je -> enrich(je, true))
                .toList();
    }

    // ── Détail ───────────────────────────────────────────────────────────────

    /**
     * Retourne le détail d'une exécution.
     *
     * @param jobExecutionId identifiant Spring Batch de l'exécution
     * @return l'exécution enrichie de ses agrégats d'étapes
     * @throws ResponseStatusException avec le statut {@code 404} si
     *         l'identifiant est inconnu
     */
    public JobExecutionDto getById(long jobExecutionId) {
        return batchRepo.findById(jobExecutionId)
                .map(je -> enrich(je, true))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Exécution de traitement introuvable : " + jobExecutionId));
    }

    // ── Steps ────────────────────────────────────────────────────────────────

    /**
     * Retourne les étapes d'une exécution.
     *
     * <p>L'existence de l'exécution est vérifiée au préalable, afin de
     * distinguer un identifiant inconnu d'une exécution sans étape : les deux
     * cas produiraient sinon une liste vide indiscernable.</p>
     *
     * @param jobExecutionId identifiant Spring Batch de l'exécution
     * @return les étapes dans leur ordre chronologique
     * @throws ResponseStatusException avec le statut {@code 404} si
     *         l'identifiant est inconnu
     */
    public List<StepExecutionDto> getSteps(long jobExecutionId) {
        // Vérifie que le job existe
        batchRepo.findById(jobExecutionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Exécution de traitement introuvable : " + jobExecutionId));

        return batchRepo.findStepsByJobExecutionId(jobExecutionId).stream()
                .map(this::toStepDto)
                .toList();
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    /**
     * Convertit une exécution en DTO, en y ajoutant si demandé les agrégats
     * calculés depuis ses étapes.
     *
     * @param je                 exécution à convertir
     * @param withStepAggregates {@code true} pour interroger les étapes et
     *                           calculer volumétrie et débit ; {@code false}
     *                           pour laisser ces compteurs à zéro
     * @return le DTO correspondant
     */
    private JobExecutionDto enrich(BatchJobExecution je, boolean withStepAggregates) {
        long readCount  = 0;
        long writeCount = 0;
        long skipCount  = 0;
        Double throughput = null;

        if (withStepAggregates) {
            List<BatchStepExecution> steps =
                    batchRepo.findStepsByJobExecutionId(je.getJobExecutionId());

            readCount  = steps.stream().mapToLong(s -> s.getReadCount()  != null ? s.getReadCount()  : 0).sum();
            writeCount = steps.stream().mapToLong(s -> s.getWriteCount() != null ? s.getWriteCount() : 0).sum();
            skipCount  = steps.stream().mapToLong(BatchStepExecution::getTotalSkipCount).sum();

            Long dur = je.getDurationSeconds();
            if (dur != null && dur > 0 && writeCount > 0) {
                throughput = (double) writeCount / dur;
            }
        }

        // Réconciliation pour ce job
        ReconciliationDto reconciliation = resolveReconciliation(je.getJobExecutionId());

        return JobExecutionDto.builder()
                .jobExecutionId(je.getJobExecutionId())
                .jobInstanceId(je.getJobInstanceId())
                .jobName(je.getJobName())
                .status(je.getStatus())
                .exitCode(je.getExitCode())
                .exitMessage(truncate(je.getExitMessage(), 500))
                .startTime(je.getStartTime())
                .endTime(je.getEndTime())
                .createTime(je.getCreateTime())
                .durationSeconds(je.getDurationSeconds())
                .running(je.isRunning())
                .failed(je.isFailed())
                .totalReadCount(readCount)
                .totalWriteCount(writeCount)
                .totalSkipCount(skipCount)
                .throughputPerSecond(throughput)
                .reconciliation(reconciliation)
                .build();
    }

    /**
     * Convertit une étape en DTO, en normalisant les compteurs absents à zéro.
     *
     * @param se étape à convertir
     * @return le DTO correspondant
     */
    private StepExecutionDto toStepDto(BatchStepExecution se) {
        return StepExecutionDto.builder()
                .stepExecutionId(se.getStepExecutionId())
                .jobExecutionId(se.getJobExecutionId())
                .stepName(se.getStepName())
                .status(se.getStatus())
                .exitCode(se.getExitCode())
                .startTime(se.getStartTime())
                .endTime(se.getEndTime())
                .durationSeconds(se.getDurationSeconds())
                .readCount(se.getReadCount()  != null ? se.getReadCount()  : 0)
                .writeCount(se.getWriteCount() != null ? se.getWriteCount() : 0)
                .filterCount(se.getFilterCount() != null ? se.getFilterCount() : 0)
                .skipCount(se.getTotalSkipCount())
                .rollbackCount(se.getRollbackCount() != null ? se.getRollbackCount() : 0)
                .commitCount(se.getCommitCount()   != null ? se.getCommitCount()   : 0)
                .throughputPerSecond(se.getThroughputPerSecond())
                .build();
    }

    /**
     * Recherche le comptage de réconciliation produit par une exécution.
     *
     * @param jobExecutionId identifiant Spring Batch de l'exécution
     * @return la réconciliation correspondante, ou {@code null} si l'exécution
     *         n'a produit aucun comptage
     */
    private ReconciliationDto resolveReconciliation(long jobExecutionId) {
        return statsRepo.findByTable("", 1).stream()
                .filter(s -> s.getJobExecutionId() == jobExecutionId)
                .findFirst()
                .map(s -> ReconciliationDto.builder()
                        .tableName(s.getMssqlTable())
                        .businessDate(s.getBusinessDate())
                        .oracleCount(s.getOracleCount())
                        .sqlServerCount(s.getSqlCount())
                        .delta(s.getOracleCount() - s.getSqlCount())
                        .reconciliationRate(s.getReconciliationRate() != null ? s.getReconciliationRate() : 0.0)
                        .reconciled(s.isReconciled(5.0))
                        .status(s.isReconciled(5.0) ? "Conforme" : "Non conforme")
                        .build())
                .orElse(null);
    }

    /**
     * Tronque une chaîne trop longue en la suffixant de points de suspension.
     *
     * <p>Appliqué aux messages de sortie, qui peuvent contenir une trace
     * d'exception entière.</p>
     *
     * @param s   chaîne à tronquer, éventuellement {@code null}
     * @param max longueur maximale conservée
     * @return la chaîne éventuellement tronquée, ou {@code null} si l'entrée l'était
     */
    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}

