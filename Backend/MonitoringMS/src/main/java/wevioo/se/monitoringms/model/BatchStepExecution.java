package wevioo.se.monitoringms.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Étape (« step ») d'une exécution Spring Batch, mappée depuis
 * {@code BATCH_STEP_EXECUTION}.
 *
 * <p>Porte les métriques de volumétrie utilisées par les indicateurs :
 * lignes lues, écrites, filtrées, ignorées et transactions annulées.
 * Les compteurs sont nullables car Spring Batch ne les renseigne pas
 * systématiquement selon le type d'étape.</p>
 *
 * @see BatchJobExecution exécution à laquelle appartient cette étape
 */
@Data
@Builder
public class BatchStepExecution {

    /** Clé primaire de l'étape ({@code BATCH_STEP_EXECUTION.STEP_EXECUTION_ID}). */
    private Long    stepExecutionId;

    /** Exécution de traitement à laquelle cette étape appartient. */
    private Long    jobExecutionId;

    /** Nom de l'étape tel que déclaré dans la définition du traitement. */
    private String  stepName;

    /** Numéro de version optimiste géré par Spring Batch. */
    private Integer version;

    /** Date de création de l'enregistrement. */
    private LocalDateTime createTime;

    /** Date de démarrage effectif ; {@code null} tant que l'étape n'a pas commencé. */
    private LocalDateTime startTime;

    /** Date de fin ; {@code null} tant que l'étape est en cours. */
    private LocalDateTime endTime;

    /** Statut Spring Batch de l'étape. */
    private String  status;

    /** Nombre de transactions validées. */
    private Long    commitCount;

    /** Nombre de lignes lues depuis la source Oracle. */
    private Long    readCount;

    /** Nombre de lignes écartées par le processeur sans être une erreur. */
    private Long    filterCount;

    /** Nombre de lignes effectivement écrites en SQL Server. */
    private Long    writeCount;

    /** Lignes ignorées lors de la lecture. */
    private Long    readSkipCount;

    /** Lignes ignorées lors de l'écriture. */
    private Long    writeSkipCount;

    /** Lignes ignorées lors du traitement. */
    private Long    processSkipCount;

    /** Nombre de transactions annulées. */
    private Long    rollbackCount;

    /** Code de sortie Spring Batch de l'étape. */
    private String  exitCode;

    /** Message de sortie ; contient la trace d'erreur en cas d'échec. */
    private String  exitMessage;

    /** Date de dernière modification de la ligne par Spring Batch. */
    private LocalDateTime lastUpdated;

    /**
     * Total des lignes ignorées, toutes phases confondues.
     *
     * <p>Somme des compteurs de lecture, de traitement et d'écriture ;
     * un compteur non renseigné est traité comme zéro.</p>
     *
     * @return le nombre total de lignes ignorées, jamais négatif
     */
    public long getTotalSkipCount() {
        return safeVal(readSkipCount) + safeVal(processSkipCount) + safeVal(writeSkipCount);
    }

    /**
     * Durée de l'étape en secondes.
     *
     * <p>Pour une étape encore en cours, la durée est mesurée jusqu'à
     * l'instant présent.</p>
     *
     * @return la durée en secondes, ou {@code null} si l'étape n'a jamais démarré
     */
    public Long getDurationSeconds() {
        if (startTime == null) return null;
        var end = (endTime != null) ? endTime : LocalDateTime.now();
        return java.time.Duration.between(startTime, end).getSeconds();
    }

    /**
     * Débit d'écriture de l'étape, en lignes par seconde.
     *
     * @return le débit, ou {@code null} si l'étape n'a pas démarré, a duré
     *         moins d'une seconde, ou n'a rien écrit — ces trois cas rendent
     *         le rapport ininterprétable
     */
    public Double getThroughputPerSecond() {
        Long dur = getDurationSeconds();
        if (dur == null || dur == 0 || writeCount == null) return null;
        return (double) writeCount / dur;
    }

    /**
     * Convertit un compteur nullable en valeur exploitable.
     *
     * @param v le compteur, éventuellement {@code null}
     * @return la valeur du compteur, ou {@code 0} s'il n'est pas renseigné
     */
    private long safeVal(Long v) {
        return v == null ? 0L : v;
    }
}
