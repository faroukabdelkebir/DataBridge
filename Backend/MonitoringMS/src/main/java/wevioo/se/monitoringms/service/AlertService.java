package wevioo.se.monitoringms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import wevioo.se.monitoringms.dto.AlertDto;
import wevioo.se.monitoringms.dto.ReconciliationDto;
import wevioo.se.monitoringms.dto.SlaStatusDto;
import wevioo.se.monitoringms.repository.BatchExecutionRepository;
import wevioo.se.monitoringms.repository.SchemaRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Moteur d'alertes : évalue périodiquement les conditions de surveillance et
 * maintient en mémoire la liste des alertes actives.
 *
 * <p>Familles d'alertes produites :</p>
 * <ul>
 *   <li>{@code JOB_FAILURE} — un traitement vient d'échouer ;</li>
 *   <li>{@code CONSECUTIVE_FAILURE} — le seuil d'échecs répétés est atteint ;</li>
 *   <li>{@code SLA_BREACH} — un traitement a dépassé son heure limite ;</li>
 *   <li>{@code RECONCILIATION_KO} — l'écart Oracle / SQL Server sort de la tolérance ;</li>
 *   <li>{@code SCHEMA_DRIFT} — le schéma T24 a été modifié récemment.</li>
 * </ul>
 *
 * <p>Les alertes ne sont pas persistées : elles vivent dans une carte en
 * mémoire, reconstruite à chaque évaluation. Un redémarrage vide donc la
 * liste, qui se reforme à l'évaluation suivante si les conditions
 * persistent.</p>
 *
 * <p>L'évaluation est idempotente : chaque passage réinsère les alertes dont
 * la condition tient toujours et retire celles dont la cause a disparu.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    /** Gravité la plus élevée ; la traduction est faite côté client. */
    private static final String SEVERITY_CRITICAL = "CRITICAL";

    /** Gravité intermédiaire, signalant une dérive à surveiller. */
    private static final String SEVERITY_WARNING  = "WARNING";

    /** Gravité informative, sans action immédiate attendue. */
    private static final String SEVERITY_INFO     = "INFO";

    /**
     * Statut renvoyé par {@link ReconciliationService} pour un écart modéré.
     * Comparé tel quel car le service produit des libellés en français.
     */
    private static final String RECON_STATUS_WARNING = "Avertissement";

    /** Accès aux métadonnées Spring Batch, pour le décompte des échecs. */
    private final BatchExecutionRepository batchRepo;

    /** Source des écarts de réconciliation. */
    private final ReconciliationService     reconciliationService;

    /** Source des dépassements de fenêtre SLA. */
    private final SlaService                slaService;

    /** Accès à l'audit de schéma, pour la détection des dérives. */
    private final SchemaRepository schemaRepo;

    /** Nombre d'échecs à partir duquel l'alerte passe en gravité critique. */
    @Value("${monitor.alert.consecutive-failure-threshold:3}")
    private int consecutiveFailureThreshold;

    /**
     * Alertes actives, indexées par leur clé stable. La carte est concurrente :
     * l'évaluation planifiée y écrit pendant que les requêtes HTTP la lisent.
     */
    private final Map<String, AlertDto> activeAlertsMap = new ConcurrentHashMap<>();

    // ── Accès public ─────────────────────────────────────────────────────────

    /**
     * Retourne les alertes actuellement actives.
     *
     * @return une copie de la liste des alertes, sûre à parcourir même si
     *         l'évaluation planifiée survient entre-temps
     */
    public List<AlertDto> getActiveAlerts() {
        return new ArrayList<>(activeAlertsMap.values());
    }

    /**
     * Acquitte une alerte et la retire de la liste active.
     *
     * <p>L'acquittement n'est pas définitif : si la condition d'origine
     * persiste, la prochaine évaluation recréera l'alerte.</p>
     *
     * @param alertKey clé de l'alerte à acquitter ; une clé inconnue est
     *                 ignorée sans erreur
     */
    public void resolveAlert(String alertKey) {
        activeAlertsMap.computeIfPresent(alertKey, (k, v) -> {
            v.setResolved(true);
            return v;
        });
        activeAlertsMap.remove(alertKey);
    }

    // ── Évaluation périodique ────────────────────────────────────────────────

    /**
     * Réévalue l'ensemble des conditions d'alerte.
     *
     * <p>Déclenchée automatiquement chaque minute par défaut, la cadence étant
     * pilotée par la propriété {@code extractor.scheduler.poll-ms}. Chaque
     * famille est évaluée indépendamment et met à jour la liste active.</p>
     */
    @Scheduled(fixedDelayString = "${extractor.scheduler.poll-ms:60000}")
    public void evaluateAlerts() {
        log.debug("Évaluation des alertes...");
        evaluateJobFailures();
        evaluateSlaBreaches();
        evaluateReconciliation();
        evaluateSchemaDrift();
    }

    // ── Évaluation des jobs ──────────────────────────────────────────────────

    /**
     * Évalue l'état d'échec des traitements surveillés.
     *
     * <p>Trois issues par traitement : au-delà du seuil configuré l'alerte est
     * critique, un échec isolé produit un simple avertissement, et l'absence
     * d'échec purge les deux alertes éventuellement présentes.</p>
     */
    private void evaluateJobFailures() {
        List<String> jobNames = List.of("T24", "CUSTOMER.JOB", "AA.BILL.DETAILS.JOB", "FT.HIS.JOB");

        for (String jobName : jobNames) {
            int consecutive = batchRepo.countConsecutiveFailures(jobName);

            if (consecutive >= consecutiveFailureThreshold) {
                String key = "CONSECUTIVE_FAILURE_" + jobName;
                activeAlertsMap.put(key, AlertDto.builder()
                        .alertKey(key)
                        .typeKey("alerts.kind.consecutiveFailure")
                        .severity(SEVERITY_CRITICAL)
                        .jobName(jobName)
                        .messageKey("alerts.msg.consecutiveFailure")
                        .params(Map.of("count", consecutive, "job", jobName))
                        .triggeredAt(LocalDateTime.now())
                        .resolved(false)
                        .build());
                log.warn("[ALERTE] Échecs consécutifs sur {} : {}", jobName, consecutive);

            } else if (consecutive == 1) {
                String key = "JOB_FAILURE_" + jobName;
                activeAlertsMap.put(key, AlertDto.builder()
                        .alertKey(key)
                        .typeKey("alerts.kind.jobFailure")
                        .severity(SEVERITY_WARNING)
                        .jobName(jobName)
                        .messageKey("alerts.msg.jobFailure")
                        .params(Map.of("job", jobName))
                        .triggeredAt(LocalDateTime.now())
                        .resolved(false)
                        .build());
            } else {
                // Succès → nettoyer les alertes liées à ce job
                activeAlertsMap.remove("JOB_FAILURE_" + jobName);
                activeAlertsMap.remove("CONSECUTIVE_FAILURE_" + jobName);
            }
        }
    }

    // ── SLA ──────────────────────────────────────────────────────────────────

    /**
     * Évalue le respect des fenêtres SLA.
     *
     * <p>Un dépassement de plus d'une heure est jugé critique ; en deçà,
     * l'alerte reste un avertissement.</p>
     */
    private void evaluateSlaBreaches() {
        for (SlaStatusDto sla : slaService.getAllSlaStatuses()) {
            String key = "SLA_BREACH_" + sla.getJobName();

            if (sla.isSlaBreached()) {
                long lateMinutes = sla.getLateBySeconds() / 60;
                activeAlertsMap.put(key, AlertDto.builder()
                        .alertKey(key)
                        .typeKey("alerts.kind.slaBreach")
                        .severity(lateMinutes > 60 ? SEVERITY_CRITICAL : SEVERITY_WARNING)
                        .jobName(sla.getJobName())
                        .messageKey("alerts.msg.slaBreach")
                        .params(Map.of("job", sla.getJobName(), "minutes", lateMinutes))
                        .triggeredAt(LocalDateTime.now())
                        .resolved(false)
                        .build());
            } else {
                activeAlertsMap.remove(key);
            }
        }
    }

    // ── Réconciliation ───────────────────────────────────────────────────────

    /**
     * Évalue les écarts de réconciliation Oracle ↔ SQL Server.
     *
     * <p>Une table non conforme déclenche une alerte critique, un simple
     * avertissement une alerte de moindre gravité ; une table conforme purge
     * l'alerte existante. Le taux est formaté en {@link Locale#ROOT} pour que
     * le séparateur décimal ne dépende pas de la locale du serveur.</p>
     */
    private void evaluateReconciliation() {
        for (ReconciliationDto r : reconciliationService.getLatestReconciliation()) {
            String key = "RECONCILIATION_KO_" + r.getTableName();

            if ("Non conforme".equals(r.getStatus())) {
                activeAlertsMap.put(key, AlertDto.builder()
                        .alertKey(key)
                        .typeKey("alerts.kind.reconciliation")
                        .severity(SEVERITY_CRITICAL)
                        .jobName(r.getTableName())
                        .messageKey("alerts.msg.reconciliationKo")
                        .params(Map.of(
                                "table", r.getTableName(),
                                "oracle", r.getOracleCount(),
                                "sql", r.getSqlServerCount(),
                                "rate", String.format(Locale.ROOT, "%.1f", r.getReconciliationRate())))
                        .triggeredAt(LocalDateTime.now())
                        .resolved(false)
                        .build());
            } else if (RECON_STATUS_WARNING.equals(r.getStatus())) {
                activeAlertsMap.put(key, AlertDto.builder()
                        .alertKey(key)
                        .typeKey("alerts.kind.reconciliation")
                        .severity(SEVERITY_WARNING)
                        .jobName(r.getTableName())
                        .messageKey("alerts.msg.reconciliationWarn")
                        .params(Map.of(
                                "table", r.getTableName(),
                                "rate", String.format(Locale.ROOT, "%.1f", r.getReconciliationRate())))
                        .triggeredAt(LocalDateTime.now())
                        .resolved(false)
                        .build());
            } else {
                activeAlertsMap.remove(key);
            }
        }
    }

    // ── Schema drift ─────────────────────────────────────────────────────────

    /**
     * Signale les modifications récentes du dictionnaire de schéma.
     *
     * <p>La fenêtre d'observation est de vingt-quatre heures. L'alerte reste
     * informative : une évolution de schéma n'est pas une anomalie en soi,
     * mais elle explique souvent l'apparition ou la disparition d'indicateurs
     * analytiques.</p>
     */
    private void evaluateSchemaDrift() {
        int drifts = schemaRepo.countRecentDrifts(1); // dernières 24h
        String key = "SCHEMA_DRIFT";

        if (drifts > 0) {
            activeAlertsMap.put(key, AlertDto.builder()
                    .alertKey(key)
                    .typeKey("alerts.kind.schemaDrift")
                    .severity(SEVERITY_INFO)
                    .jobName("SCHEMA_SOURCE")
                    .messageKey("alerts.msg.schemaDrift")
                    .params(Map.of("count", drifts))
                    .triggeredAt(LocalDateTime.now())
                    .resolved(false)
                    .build());
        } else {
            activeAlertsMap.remove(key);
        }
    }
}

