package wevioo.se.monitoringms.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Étape d'une exécution, exposée par l'API lors du drill-down.
 *
 * <p>Contrairement au modèle {@link wevioo.se.monitoringms.model.BatchStepExecution},
 * les compteurs sont ici toujours renseignés : les valeurs absentes côté base
 * sont normalisées à zéro pour simplifier l'affichage.</p>
 *
 * @see wevioo.se.monitoringms.service.JobService#getSteps(long)
 */
@Data
@Builder
public class StepExecutionDto {

    /** Identifiant de l'étape Spring Batch. */
    private Long          stepExecutionId;

    /** Exécution de traitement à laquelle cette étape appartient. */
    private Long          jobExecutionId;

    /** Nom de l'étape tel que déclaré dans la définition du traitement. */
    private String        stepName;

    /** Statut Spring Batch de l'étape. */
    private String        status;

    /** Code de sortie Spring Batch de l'étape. */
    private String        exitCode;

    /** Date de démarrage effectif. */
    private LocalDateTime startTime;

    /** Date de fin ; {@code null} tant que l'étape est en cours. */
    private LocalDateTime endTime;

    /** Durée en secondes ; mesurée jusqu'à l'instant présent si l'étape tourne. */
    private Long          durationSeconds;

    /** Lignes lues depuis la source Oracle. */
    private long          readCount;

    /** Lignes effectivement écrites en SQL Server. */
    private long          writeCount;

    /** Lignes écartées par le processeur sans être une erreur. */
    private long          filterCount;

    /** Total des lignes ignorées, toutes phases confondues. */
    private long          skipCount;

    /** Nombre de transactions annulées. */
    private long          rollbackCount;

    /** Nombre de transactions validées. */
    private long          commitCount;

    /**
     * Débit d'écriture en lignes par seconde ; {@code null} pour une étape
     * de moins d'une seconde ou sans écriture.
     */
    private Double        throughputPerSecond;
}
