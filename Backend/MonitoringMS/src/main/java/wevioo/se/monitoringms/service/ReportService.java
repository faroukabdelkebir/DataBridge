package wevioo.se.monitoringms.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import wevioo.se.monitoringms.dto.DailyVolumeDto;
import wevioo.se.monitoringms.dto.KpiDto;
import wevioo.se.monitoringms.dto.ReconciliationDto;
import wevioo.se.monitoringms.dto.SlaStatusDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Génération des rapports récapitulatifs du processus d'extraction.
 *
 * <p>Deux rapports sont produits : un rapport journalier consolidant
 * indicateurs, réconciliation, SLA et alertes ; un rapport hebdomadaire
 * centré sur les tendances et la comparaison à la semaine précédente.</p>
 *
 * <p>Les deux sont mis en cache quinze minutes, avec une clé intégrant la date
 * du jour ou le lundi de la semaine courante : le passage à une nouvelle
 * période invalide donc naturellement l'entrée.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    /** Source des indicateurs de performance. */
    private final KpiService            kpiService;

    /** Source des écarts de réconciliation. */
    private final ReconciliationService reconciliationService;

    /** Source des états de respect des fenêtres SLA. */
    private final SlaService            slaService;

    /** Source des alertes actives. */
    private final AlertService          alertService;

    /**
     * Produit le rapport journalier consolidé.
     *
     * <p>Les indicateurs sont calculés sur deux fenêtres — la journée et les
     * sept derniers jours — afin de replacer la situation du jour dans sa
     * tendance.</p>
     *
     * @return le rapport du jour, horodaté par son instant de génération
     */
    @Cacheable(value = "reports", key = "'daily_' + T(java.time.LocalDate).now().toString()")
    public DailyReport generateDailyReport() {
        log.info("Génération du rapport journalier pour le {}", LocalDate.now());

        KpiDto kpis1d = kpiService.computeKpis(1);
        KpiDto kpis7d = kpiService.computeKpis(7);

        return DailyReport.builder()
                .reportDate(LocalDate.now())
                .generatedAt(LocalDateTime.now())
                .kpis1Day(kpis1d)
                .kpis7Days(kpis7d)
                .reconciliations(reconciliationService.getLatestReconciliation())
                .slaStatuses(slaService.getAllSlaStatuses())
                .activeAlerts(alertService.getActiveAlerts().stream()
                        .map(a -> AlertSummary.builder()
                                .type(a.getType())
                                .severity(a.getSeverity())
                                .jobName(a.getJobName())
                                .message(a.getMessage())
                                .build())
                        .toList())
                .overallStatus(computeOverallStatus(kpis1d))
                .build();
    }

    /**
     * Produit le rapport hebdomadaire.
     *
     * <p>Compare la semaine écoulée à la précédente et met en avant la table
     * la plus volumineuse.</p>
     *
     * @return le rapport de la semaine, horodaté par son instant de génération
     */
    @Cacheable(value = "reports", key = "'weekly_' + T(java.time.LocalDate).now().with(T(java.time.DayOfWeek).MONDAY).toString()")
    public WeeklyReport generateWeeklyReport() {
        KpiDto kpis7d  = kpiService.computeKpis(7);
        KpiDto kpis14d = kpiService.computeKpis(14);

        // Comparaison semaine N vs N-1
        double successRateDelta = kpis7d.getOverallSuccessRate()
                - computePreviousWeekRate(kpis14d, kpis7d);

        return WeeklyReport.builder()
                .weekStart(LocalDate.now().minusDays(6))
                .weekEnd(LocalDate.now())
                .generatedAt(LocalDateTime.now())
                .kpis(kpis7d)
                .successRateDeltaVsPreviousWeek(Math.round(successRateDelta * 100.0) / 100.0)
                .topVolumeTable(resolveTopVolumeTable(kpis7d))
                .trend(kpis7d.getVolumeTrend())
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Qualifie l'état général du processus à partir des indicateurs du jour.
     *
     * <p>Tout échec dégrade l'état, quel que soit le taux de succès : un
     * incident isolé reste un incident.</p>
     *
     * @param kpis indicateurs de la journée
     * @return « Dégradé », « Opérationnel », « Partiel » ou « Critique »
     */
    private String computeOverallStatus(KpiDto kpis) {
        if (kpis.getFailureCount() > 0)            return "Dégradé";
        if (kpis.getOverallSuccessRate() == 100.0) return "Opérationnel";
        if (kpis.getOverallSuccessRate() >= 80.0)  return "Partiel";
        return "Critique";
    }

    /**
     * Estime le taux de succès de la semaine précédente.
     *
     * <p>Approximation assumée : le taux sur quatorze jours est traité comme
     * la moyenne des deux semaines, d'où {@code 2 × taux14j − taux7j}. Le
     * calcul suppose un volume d'exécutions comparable d'une semaine à
     * l'autre ; l'écart obtenu vaut comme tendance, non comme mesure
     * exacte.</p>
     *
     * @param kpis14d indicateurs sur quatorze jours
     * @param kpis7d  indicateurs sur sept jours
     * @return le taux estimé de la semaine précédente, en pourcentage
     */
    private double computePreviousWeekRate(KpiDto kpis14d, KpiDto kpis7d) {
        return 2 * kpis14d.getOverallSuccessRate() - kpis7d.getOverallSuccessRate();
    }

    /**
     * Identifie la table ayant reçu le plus de lignes sur la période.
     *
     * @param kpis indicateurs de la période
     * @return le nom de la table, ou « Non disponible » si aucune volumétrie
     *         n'est connue
     */
    private String resolveTopVolumeTable(KpiDto kpis) {
        return kpis.getRowsByTable().entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElse("Non disponible");
    }

    // ── Inner DTOs ────────────────────────────────────────────────────────────

    /** Rapport journalier consolidé du processus d'extraction. */
    @Data
    @Builder
    public static class DailyReport {

        /** Journée couverte par le rapport. */
        private LocalDate                reportDate;

        /** Instant de génération, utile pour interpréter les données mises en cache. */
        private LocalDateTime            generatedAt;

        /** Indicateurs de la journée. */
        private KpiDto                   kpis1Day;

        /** Indicateurs des sept derniers jours, pour situer la tendance. */
        private KpiDto                   kpis7Days;

        /** État de réconciliation de chaque table extraite. */
        private List<ReconciliationDto> reconciliations;

        /** État de respect des fenêtres SLA. */
        private List<SlaStatusDto>       slaStatuses;

        /** Alertes actives au moment de la génération. */
        private List<AlertSummary>       activeAlerts;

        /** Qualification de l'état général du processus. */
        private String                   overallStatus;
    }

    /** Rapport hebdomadaire, centré sur les tendances. */
    @Data @Builder
    public static class WeeklyReport {

        /** Premier jour de la semaine couverte. */
        private LocalDate              weekStart;

        /** Dernier jour de la semaine couverte. */
        private LocalDate              weekEnd;

        /** Instant de génération du rapport. */
        private LocalDateTime          generatedAt;

        /** Indicateurs des sept derniers jours. */
        private KpiDto kpis;

        /**
         * Écart de taux de succès face à la semaine précédente, en points.
         * Repose sur une estimation ; à lire comme une tendance.
         */
        private double                 successRateDeltaVsPreviousWeek;

        /** Table ayant reçu le plus de lignes sur la période. */
        private String                 topVolumeTable;

        /** Volumétrie jour par jour sur la semaine. */
        private List<DailyVolumeDto>   trend;
    }

    /** Vue allégée d'une alerte, telle qu'incluse dans un rapport. */
    @Data @Builder
    public static class AlertSummary {

        /** Type d'alerte. */
        private String type;

        /** Gravité : {@code CRITICAL}, {@code WARNING} ou {@code INFO}. */
        private String severity;

        /** Traitement concerné. */
        private String jobName;

        /** Message brut ; peut être vide, le moteur d'alertes produisant des clés d'internationalisation. */
        private String message;
    }
}
