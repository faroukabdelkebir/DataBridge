package wevioo.se.monitoringms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import wevioo.se.monitoringms.dto.SlaStatusDto;
import wevioo.se.monitoringms.model.BatchJobExecution;
import wevioo.se.monitoringms.repository.BatchExecutionRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Suivi du respect des fenêtres SLA : chaque traitement doit s'achever avant
 * une heure limite quotidienne.
 *
 * <p>Les heures limites sont définies dans {@code application.properties} sous
 * les clés {@code monitor.sla.customer}, {@code monitor.sla.ft-his},
 * {@code monitor.sla.aa-bill} et {@code monitor.sla.main}.</p>
 *
 * <p>Une fenêtre est dépassée dans deux cas : le traitement s'est terminé
 * après l'heure limite, ou il tourne encore alors que celle-ci est passée.
 * Un traitement jamais exécuté n'est pas considéré en dépassement, l'absence
 * d'exécution relevant d'un autre diagnostic.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SlaService {

    /** Accès aux métadonnées Spring Batch. */
    private final BatchExecutionRepository batchRepo;

    /** Heure limite du traitement du référentiel clients. */
    @Value("${monitor.sla.customer:07:00}")
    private String slaCustomer;

    /** Heure limite du traitement des virements historisés. */
    @Value("${monitor.sla.ft-his:07:30}")
    private String slaFtHis;

    /** Heure limite du traitement du détail de facturation. */
    @Value("${monitor.sla.aa-bill:07:30}")
    private String slaAaBill;

    /** Heure limite du traitement principal, appliquée aussi par défaut. */
    @Value("${monitor.sla.main:08:00}")
    private String slaMain;

    /** Format attendu des heures limites en configuration. */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Évalue le respect des fenêtres SLA pour tous les traitements surveillés.
     *
     * @return un état par traitement, dans l'ordre de déclaration
     */
    public List<SlaStatusDto> getAllSlaStatuses() {
        Map<String, String> slaMap = buildSlaMap();

        return slaMap.entrySet().stream()
                .map(e -> computeSlaStatus(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * Évalue le respect de la fenêtre SLA d'un traitement.
     *
     * @param jobName nom logique du traitement
     * @return l'état correspondant ; un traitement non configuré se voit
     *         appliquer l'heure limite par défaut de 8 h 00
     */
    public SlaStatusDto getSlaStatus(String jobName) {
        Map<String, String> slaMap = buildSlaMap();
        String slaTime = slaMap.getOrDefault(jobName, "08:00");
        return computeSlaStatus(jobName, slaTime);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Associe chaque traitement surveillé à son heure limite configurée.
     *
     * @return la correspondance, dans un ordre d'insertion stable pour que
     *         l'affichage reste prévisible
     */
    private Map<String, String> buildSlaMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("T24",               slaMain);
        map.put("CUSTOMER.JOB",      slaCustomer);
        map.put("AA.BILL.DETAILS.JOB", slaAaBill);
        map.put("FT.HIS.JOB",        slaFtHis);
        return map;
    }

    /**
     * Confronte la dernière exécution d'un traitement à son heure limite.
     *
     * <p>Trois situations sont distinguées : une exécution réussie, comparée
     * à l'échéance ; une exécution encore en cours, en dépassement si
     * l'échéance est déjà passée ; l'absence d'exécution, signalée sans
     * constituer un dépassement.</p>
     *
     * @param jobName    nom logique du traitement
     * @param slaTimeStr heure limite au format {@code HH:mm}
     * @return l'état de respect de la fenêtre
     * @throws java.time.format.DateTimeParseException si l'heure configurée
     *         ne respecte pas le format attendu
     */
    private SlaStatusDto computeSlaStatus(String jobName, String slaTimeStr) {
        LocalTime deadline = LocalTime.parse(slaTimeStr, TIME_FMT);

        Optional<BatchJobExecution> lastSuccessful =
                batchRepo.findLastSuccessfulByJobName(jobName);

        // Pas d'exécution récente aujourd'hui
        if (lastSuccessful.isEmpty()) {
            List<BatchJobExecution> running = batchRepo.findRunning().stream()
                    .filter(j -> j.getJobName().equals(jobName))
                    .toList();

            if (!running.isEmpty()) {
                // Job en cours — vérifier si la deadline est déjà passée
                LocalDateTime now = LocalDateTime.now();
                boolean breached = now.toLocalTime().isAfter(deadline);
                long lateBy = breached
                        ? Duration.between(deadline.atDate(now.toLocalDate()), now).getSeconds()
                        : 0;

                return SlaStatusDto.builder()
                        .jobName(jobName)
                        .slaDeadline(deadline)
                        .slaBreached(breached)
                        .lateBySeconds(lateBy)
                        .status(breached ? "Délai dépassé" : "En cours")
                        .build();
            }

            return SlaStatusDto.builder()
                    .jobName(jobName)
                    .slaDeadline(deadline)
                    .slaBreached(false)
                    .lateBySeconds(0)
                    .status("Non exécuté")
                    .build();
        }

        BatchJobExecution last = lastSuccessful.get();
        LocalDateTime completionTime = last.getEndTime();

        if (completionTime == null) {
            return SlaStatusDto.builder()
                    .jobName(jobName).slaDeadline(deadline).status("Non exécuté").build();
        }

        LocalTime completionLocal = completionTime.toLocalTime();
        boolean breached = completionLocal.isAfter(deadline);

        long lateBy = 0;
        if (breached) {
            lateBy = Duration.between(
                    deadline.atDate(completionTime.toLocalDate()),
                    completionTime).getSeconds();
        }

        return SlaStatusDto.builder()
                .jobName(jobName)
                .slaDeadline(deadline)
                .lastCompletionTime(completionTime)
                .slaBreached(breached)
                .lateBySeconds(lateBy)
                .status(breached ? "Délai dépassé" : "Dans les délais")
                .build();
    }
}

