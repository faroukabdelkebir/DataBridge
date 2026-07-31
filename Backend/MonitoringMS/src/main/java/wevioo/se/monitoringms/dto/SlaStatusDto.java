package wevioo.se.monitoringms.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Respect de la fenêtre SLA par un traitement.
 *
 * <p>Chaque traitement surveillé dispose d'une heure limite quotidienne
 * définie dans la configuration ; ce DTO indique si la dernière exécution
 * réussie l'a respectée et, sinon, de combien elle a débordé.</p>
 *
 * @see wevioo.se.monitoringms.service.SlaService
 */
@Data
@Builder
public class SlaStatusDto {

    /** Nom logique du traitement surveillé. */
    private String        jobName;

    /** Heure limite quotidienne fixée par la configuration. */
    private LocalTime slaDeadline;

    /** Fin de la dernière exécution réussie ; {@code null} si aucune n'est connue. */
    private LocalDateTime lastCompletionTime;

    /** Indique si l'échéance a été dépassée. */
    private boolean       slaBreached;

    /** Retard en secondes ; vaut {@code 0} lorsque l'échéance est respectée. */
    private long          lateBySeconds;

    /** Libellé d'affichage : « Dans les délais », « Délai dépassé » ou « Non exécuté ». */
    private String        status;
}
