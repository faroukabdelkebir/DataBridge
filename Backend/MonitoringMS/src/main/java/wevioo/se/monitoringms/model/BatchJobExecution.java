package wevioo.se.monitoringms.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Exécution d'un traitement Spring Batch, telle que stockée dans les tables de
 * métadonnées de SQL Server.
 *
 * <p>Résulte de la jointure {@code BATCH_JOB_EXECUTION × BATCH_JOB_INSTANCE},
 * cette dernière apportant le nom du traitement. La classe est utilisée en
 * lecture seule : aucune annotation JPA, le mapping est réalisé manuellement
 * par un {@code RowMapper} dans
 * {@link wevioo.se.monitoringms.repository.BatchExecutionRepository}.</p>
 *
 * @see BatchStepExecution étape appartenant à cette exécution
 */
@Data
@Builder
public class BatchJobExecution {

    /** Clé primaire de l'exécution ({@code BATCH_JOB_EXECUTION.JOB_EXECUTION_ID}). */
    private Long    jobExecutionId;

    /** Instance de traitement dont cette exécution est une tentative. */
    private Long    jobInstanceId;

    /** Nom logique du traitement : {@code T24}, {@code CUSTOMER.JOB}, {@code FT.HIS.JOB}… */
    private String  jobName;

    /** Numéro de version optimiste géré par Spring Batch. */
    private Integer version;

    /** Date de création de l'enregistrement, antérieure au démarrage effectif. */
    private LocalDateTime createTime;

    /** Date de démarrage effectif ; {@code null} tant que l'exécution n'a pas commencé. */
    private LocalDateTime startTime;

    /** Date de fin ; {@code null} tant que l'exécution est en cours. */
    private LocalDateTime endTime;

    /** Statut Spring Batch : {@code STARTED}, {@code COMPLETED}, {@code FAILED}… */
    private String  status;

    /** Code de sortie Spring Batch, généralement identique au statut. */
    private String  exitCode;

    /** Message de sortie ; contient la trace d'erreur en cas d'échec. */
    private String  exitMessage;

    /** Date de dernière modification de la ligne par Spring Batch. */
    private LocalDateTime lastUpdated;

    /**
     * Durée de l'exécution en secondes.
     *
     * <p>Pour un traitement encore en cours, la durée est mesurée jusqu'à
     * l'instant présent afin d'alimenter le compteur temps réel du tableau
     * de bord.</p>
     *
     * @return la durée en secondes, ou {@code null} si le traitement n'a jamais démarré
     */
    public Long getDurationSeconds() {
        if (startTime == null) return null;
        var end = (endTime != null) ? endTime : LocalDateTime.now();
        return java.time.Duration.between(startTime, end).getSeconds();
    }

    /**
     * Indique si le traitement est actuellement actif.
     *
     * <p>La comparaison est sensible à la casse : seules les valeurs exactes
     * écrites par Spring Batch sont reconnues.</p>
     *
     * @return {@code true} si le statut vaut {@code STARTED} ou {@code STARTING}
     */
    public boolean isRunning() {
        return "STARTED".equals(status) || "STARTING".equals(status);
    }

    /**
     * Indique si le traitement s'est terminé en erreur.
     *
     * @return {@code true} si le statut vaut {@code FAILED}
     */
    public boolean isFailed() {
        return "FAILED".equals(status);
    }
}
