package wevioo.se.monitoringms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import wevioo.se.monitoringms.config.CacheConfig;
import wevioo.se.monitoringms.dto.DailyVolumeDto;
import wevioo.se.monitoringms.dto.KpiDto;
import wevioo.se.monitoringms.repository.BatchExecutionRepository;
import wevioo.se.monitoringms.repository.StatsRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calcul des indicateurs de performance du processus d'extraction.
 *
 * <p>Indicateurs produits : taux de succès global et par traitement, volumes
 * extraits par table, débit moyen, durées moyennes, décompte des échecs et
 * tendance quotidienne.</p>
 *
 * <p>Le résultat est mis en cache deux minutes, la fenêtre d'analyse servant
 * de clé : chaque profondeur demandée dispose donc de sa propre entrée.</p>
 *
 * @see wevioo.se.monitoringms.dto.KpiDto
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KpiService {

    /** Accès aux métadonnées Spring Batch. */
    private final BatchExecutionRepository batchRepo;

    /** Accès aux comptages d'extraction, pour les volumétries. */
    private final StatsRepository statsRepo;

    /**
     * Calcule l'ensemble des indicateurs sur une fenêtre glissante.
     *
     * <p>Le compteur d'échecs retenu est le maximum observé parmi les
     * traitements : il traduit la situation du traitement le plus dégradé
     * plutôt qu'une moyenne qui la masquerait.</p>
     *
     * @param windowDays profondeur de la fenêtre en jours, par exemple {@code 7} ou {@code 30}
     * @return les indicateurs calculés, horodatés par leur instant de calcul
     */
    @Cacheable(value = CacheConfig.CACHE_KPIS, key = "#windowDays")
    public KpiDto computeKpis(int windowDays) {
        log.debug("Calcul des indicateurs sur une période de {} jours", windowDays);

        List<BatchExecutionRepository.JobAggregate> jobAggs    = batchRepo.computeJobAggregates(windowDays);
        List<BatchExecutionRepository.DailyAggregate> dailyAggs  = batchRepo.computeDailyTrend(windowDays);

        // ── Success rate ──────────────────────────────────────────────────────
        double overallSuccessRate = batchRepo.computeSuccessRate(windowDays);

        Map<String, Double> successRateByJob = jobAggs.stream()
                .collect(Collectors.toMap(BatchExecutionRepository.JobAggregate::jobName, BatchExecutionRepository.JobAggregate::successRate));

        // ── Volume ────────────────────────────────────────────────────────────
        long totalRowsExtracted = statsRepo.sumRowsExtracted(windowDays);

        Map<String, Long> rowsByTable = statsRepo.sumRowsByTable(windowDays).stream()
                .collect(Collectors.toMap(
                        StatsRepository.TableVolumeRecord::tableName,
                        StatsRepository.TableVolumeRecord::totalLoaded));

        // ── Throughput (avg rows/sec) ────────────────────────────────────────
        // Agrégé depuis les daily trends : total_write / total_duration_sec
        double avgThroughput = dailyAggs.stream()
                .filter(d -> d.totalWrite() > 0)
                .mapToDouble(d -> (double) d.totalWrite() / Math.max(1, d.jobCount()))
                .average().orElse(0.0);

        Map<String, Double> throughputByJob = jobAggs.stream()
                .collect(Collectors.toMap(
                        BatchExecutionRepository.JobAggregate::jobName,
                        agg -> agg.avgDurationSec() > 0
                                ? agg.completed() / agg.avgDurationSec()
                                : 0.0));

        // ── Durées moyennes ──────────────────────────────────────────────────
        double avgDuration = jobAggs.stream()
                .mapToDouble(BatchExecutionRepository.JobAggregate::avgDurationSec)
                .average().orElse(0.0);

        Map<String, Double> avgDurationByJob = jobAggs.stream()
                .collect(Collectors.toMap(BatchExecutionRepository.JobAggregate::jobName, BatchExecutionRepository.JobAggregate::avgDurationSec));

        // ── Failures ─────────────────────────────────────────────────────────
        int totalFailed = jobAggs.stream().mapToInt(BatchExecutionRepository.JobAggregate::failed).sum();
        int maxConsecutiveFailures = jobAggs.stream()
                .mapToInt(agg -> batchRepo.countConsecutiveFailures(agg.jobName()))
                .max().orElse(0);

        // ── Trend journalier ─────────────────────────────────────────────────
        List<DailyVolumeDto> trend = dailyAggs.stream()
                .map(d -> DailyVolumeDto.builder()
                        .date(d.date())
                        .rowsRead(d.totalRead())
                        .rowsWritten(d.totalWrite())
                        .jobCount(d.jobCount())
                        .successRate(d.jobCount() > 0
                                ? (double) d.completed() / d.jobCount() * 100.0
                                : 0.0)
                        .build())
                .toList();

        return KpiDto.builder()
                .windowDays(windowDays)
                .overallSuccessRate(Math.round(overallSuccessRate * 100.0) / 100.0)
                .successRateByJob(successRateByJob)
                .totalRowsExtracted(totalRowsExtracted)
                .rowsByTable(rowsByTable)
                .avgThroughputPerSec(Math.round(avgThroughput * 100.0) / 100.0)
                .throughputByJob(throughputByJob)
                .avgDurationSeconds(Math.round(avgDuration * 10.0) / 10.0)
                .avgDurationByJob(avgDurationByJob)
                .failureCount(totalFailed)
                .consecutiveFailureCount(maxConsecutiveFailures)
                .volumeTrend(trend)
                .computedAt(LocalDateTime.now())
                .build();
    }
}
