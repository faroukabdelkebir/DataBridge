package wevioo.se.monitoringms.dto;


import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;


/**
 * Exécution de traitement exposée par l'API.
 *
 * <p>Reprend les champs de {@link wevioo.se.monitoringms.model.BatchJobExecution}
 * en y ajoutant les agrégats calculés à partir des étapes ainsi que le résultat
 * de réconciliation associé. Les agrégats ne sont renseignés que sur les vues
 * de détail : les listes d'historique les laissent à zéro pour éviter une
 * requête par ligne.</p>
 *
 * @see wevioo.se.monitoringms.service.JobService
 */
@Data
@Builder
public class JobExecutionDto {

    /** Identifiant de l'exécution Spring Batch. */
    private Long          jobExecutionId;

    /** Instance de traitement dont cette exécution est une tentative. */
    private Long          jobInstanceId;

    /** Nom logique du traitement. */
    private String        jobName;

    /** Statut Spring Batch brut. */
    private String        status;

    /** Code de sortie Spring Batch. */
    private String        exitCode;

    /** Message de sortie, tronqué à 500 caractères pour alléger la réponse. */
    private String        exitMessage;

    /** Date de démarrage effectif. */
    private LocalDateTime startTime;

    /** Date de fin ; {@code null} tant que le traitement est en cours. */
    private LocalDateTime endTime;

    /** Date de création de l'enregistrement d'exécution. */
    private LocalDateTime createTime;

    /** Durée en secondes ; mesurée jusqu'à l'instant présent si le traitement tourne. */
    private Long          durationSeconds;

    /** Indique si le traitement est actuellement actif. */
    private boolean       running;

    /** Indique si le traitement s'est terminé en erreur. */
    private boolean       failed;

    /** Total des lignes lues, toutes étapes confondues. */
    private long totalReadCount;

    /** Total des lignes écrites, toutes étapes confondues. */
    private long totalWriteCount;

    /** Total des lignes ignorées, toutes étapes et toutes phases confondues. */
    private long totalSkipCount;

    /**
     * Débit global en lignes par seconde, calculé sur la durée du traitement.
     * {@code null} si la durée est nulle ou si rien n'a été écrit.
     */
    private Double throughputPerSecond;

    /**
     * Réconciliation Oracle ↔ SQL Server rattachée à cette exécution.
     * {@code null} si aucune statistique n'a été produite.
     */
    private ReconciliationDto reconciliation;
}
