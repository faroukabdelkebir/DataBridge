package wevioo.se.monitoringms.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import wevioo.se.monitoringms.model.BatchJobExecution;
import wevioo.se.monitoringms.model.BatchStepExecution;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Accès en lecture aux tables de métadonnées Spring Batch hébergées dans
 * SQL Server.
 *
 * <p>Tables interrogées : {@code BATCH_JOB_INSTANCE},
 * {@code BATCH_JOB_EXECUTION} et {@code BATCH_STEP_EXECUTION}. Ces tables
 * appartiennent au microservice d'extraction ; ce repository les consulte
 * sans jamais les modifier.</p>
 *
 * <p>Le SQL est spécifique à SQL Server : pagination {@code OFFSET … FETCH
 * NEXT}, fonctions {@code DATEADD} et {@code GETDATE}. Un changement de moteur
 * imposerait de réécrire ces requêtes.</p>
 *
 * @see wevioo.se.monitoringms.model.BatchJobExecution
 * @see wevioo.se.monitoringms.model.BatchStepExecution
 */
@Repository
@RequiredArgsConstructor
public class BatchExecutionRepository {

    /** Gabarit JDBC pointant sur la base décisionnelle SQL Server. */
    private final JdbcTemplate jdbc;

    // ── RowMappers ──────────────────────────────────────────────────────────

    /** Convertit une ligne de la jointure exécution × instance en modèle. */
    private static final RowMapper<BatchJobExecution> JOB_EXEC_MAPPER = (rs, row) ->
            BatchJobExecution.builder()
                    .jobExecutionId(rs.getLong("JOB_EXECUTION_ID"))
                    .jobInstanceId(rs.getLong("JOB_INSTANCE_ID"))
                    .jobName(rs.getString("JOB_NAME"))
                    .version(rs.getInt("VERSION"))
                    .createTime(toLocalDateTime(rs.getTimestamp("CREATE_TIME")))
                    .startTime(toLocalDateTime(rs.getTimestamp("START_TIME")))
                    .endTime(toLocalDateTime(rs.getTimestamp("END_TIME")))
                    .status(rs.getString("STATUS"))
                    .exitCode(rs.getString("EXIT_CODE"))
                    .exitMessage(rs.getString("EXIT_MESSAGE"))
                    .lastUpdated(toLocalDateTime(rs.getTimestamp("LAST_UPDATED")))
                    .build();

    /** Convertit une ligne de {@code BATCH_STEP_EXECUTION} en modèle. */
    private static final RowMapper<BatchStepExecution> STEP_EXEC_MAPPER = (rs, row) ->
            BatchStepExecution.builder()
                    .stepExecutionId(rs.getLong("STEP_EXECUTION_ID"))
                    .jobExecutionId(rs.getLong("JOB_EXECUTION_ID"))
                    .stepName(rs.getString("STEP_NAME"))
                    .version(rs.getInt("VERSION"))
                    .createTime(toLocalDateTime(rs.getTimestamp("CREATE_TIME")))
                    .startTime(toLocalDateTime(rs.getTimestamp("START_TIME")))
                    .endTime(toLocalDateTime(rs.getTimestamp("END_TIME")))
                    .status(rs.getString("STATUS"))
                    .commitCount(rs.getLong("COMMIT_COUNT"))
                    .readCount(rs.getLong("READ_COUNT"))
                    .filterCount(rs.getLong("FILTER_COUNT"))
                    .writeCount(rs.getLong("WRITE_COUNT"))
                    .readSkipCount(rs.getLong("READ_SKIP_COUNT"))
                    .writeSkipCount(rs.getLong("WRITE_SKIP_COUNT"))
                    .processSkipCount(rs.getLong("PROCESS_SKIP_COUNT"))
                    .rollbackCount(rs.getLong("ROLLBACK_COUNT"))
                    .exitCode(rs.getString("EXIT_CODE"))
                    .exitMessage(rs.getString("EXIT_MESSAGE"))
                    .lastUpdated(toLocalDateTime(rs.getTimestamp("LAST_UPDATED")))
                    .build();

    // ── Queries ─────────────────────────────────────────────────────────────

    /**
     * Projection commune à toutes les recherches d'exécutions : la jointure
     * avec {@code BATCH_JOB_INSTANCE} apporte le nom du traitement, absent de
     * la table d'exécution. Les clauses de filtrage et de tri sont concaténées
     * par les méthodes appelantes.
     */
    private static final String BASE_JOB_SELECT = """
        SELECT je.JOB_EXECUTION_ID, je.JOB_INSTANCE_ID, je.VERSION,
               je.CREATE_TIME, je.START_TIME, je.END_TIME,
               je.STATUS, je.EXIT_CODE, je.EXIT_MESSAGE, je.LAST_UPDATED,
               ji.JOB_NAME
        FROM BATCH_JOB_EXECUTION je
        INNER JOIN BATCH_JOB_INSTANCE ji ON ji.JOB_INSTANCE_ID = je.JOB_INSTANCE_ID
        """;

    /**
     * Recherche les traitements actuellement actifs.
     *
     * @return les exécutions au statut {@code STARTED} ou {@code STARTING},
     *         de la plus récemment démarrée à la plus ancienne
     */
    public List<BatchJobExecution> findRunning() {
        return jdbc.query(
                BASE_JOB_SELECT + " WHERE je.STATUS IN ('STARTED','STARTING') ORDER BY je.START_TIME DESC",
                JOB_EXEC_MAPPER);
    }

    /**
     * Recherche les dernières exécutions, tous traitements confondus.
     *
     * @param limit nombre maximal de lignes à ramener
     * @return les exécutions les plus récentes, du plus récent au plus ancien
     */
    public List<BatchJobExecution> findRecent(int limit) {
        return jdbc.query(
                BASE_JOB_SELECT + " ORDER BY je.CREATE_TIME DESC OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY",
                JOB_EXEC_MAPPER, limit);
    }

    /**
     * Recherche les exécutions créées sur une fenêtre glissante.
     *
     * @param days profondeur de la fenêtre, en jours comptés depuis maintenant
     * @return les exécutions de la période, du plus récent au plus ancien
     */
    public List<BatchJobExecution> findByDays(int days) {
        return jdbc.query(
                BASE_JOB_SELECT + " WHERE je.CREATE_TIME >= DATEADD(day, -?, GETDATE()) ORDER BY je.CREATE_TIME DESC",
                JOB_EXEC_MAPPER, days);
    }

    /**
     * Recherche l'historique d'un traitement donné.
     *
     * @param jobName nom logique du traitement, par exemple {@code CUSTOMER.JOB}
     * @param limit   nombre maximal de lignes à ramener
     * @return les exécutions de ce traitement, du plus récent au plus ancien ;
     *         liste vide si le nom est inconnu
     */
    public List<BatchJobExecution> findByJobName(String jobName, int limit) {
        return jdbc.query(
                BASE_JOB_SELECT + " WHERE ji.JOB_NAME = ? ORDER BY je.CREATE_TIME DESC OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY",
                JOB_EXEC_MAPPER, jobName, limit);
    }

    /**
     * Recherche une exécution par son identifiant.
     *
     * @param jobExecutionId identifiant Spring Batch de l'exécution
     * @return l'exécution correspondante, ou {@link Optional#empty()} si
     *         l'identifiant est inconnu
     */
    public Optional<BatchJobExecution> findById(long jobExecutionId) {
        List<BatchJobExecution> result = jdbc.query(
                BASE_JOB_SELECT + " WHERE je.JOB_EXECUTION_ID = ?",
                JOB_EXEC_MAPPER, jobExecutionId);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    /**
     * Recherche les étapes d'une exécution.
     *
     * @param jobExecutionId identifiant Spring Batch de l'exécution
     * @return les étapes dans leur ordre chronologique de création ; liste vide
     *         si l'exécution est inconnue ou n'a produit aucune étape
     */
    public List<BatchStepExecution> findStepsByJobExecutionId(long jobExecutionId) {
        return jdbc.query(
                """
                SELECT STEP_EXECUTION_ID, JOB_EXECUTION_ID, STEP_NAME, VERSION,
                       CREATE_TIME, START_TIME, END_TIME, STATUS,
                       COMMIT_COUNT, READ_COUNT, FILTER_COUNT, WRITE_COUNT,
                       READ_SKIP_COUNT, WRITE_SKIP_COUNT, PROCESS_SKIP_COUNT,
                       ROLLBACK_COUNT, EXIT_CODE, EXIT_MESSAGE, LAST_UPDATED
                FROM BATCH_STEP_EXECUTION
                WHERE JOB_EXECUTION_ID = ?
                ORDER BY CREATE_TIME ASC
                """,
                STEP_EXEC_MAPPER, jobExecutionId);
    }

    /**
     * Compte les exécutions terminées avec succès depuis minuit.
     *
     * <p>Le décompte s'appuie sur la date de fin, ce qui exclut les
     * traitements démarrés hier et toujours en cours.</p>
     *
     * @return le nombre d'exécutions au statut {@code COMPLETED} ce jour,
     *         ou {@code 0} si l'agrégat n'a pu être calculé
     */
    public int countCompletedToday() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM BATCH_JOB_EXECUTION WHERE STATUS = 'COMPLETED' AND CAST(END_TIME AS DATE) = CAST(GETDATE() AS DATE)",
                Integer.class);
        return count != null ? count : 0;
    }

    /**
     * Compte les exécutions en échec depuis minuit.
     *
     * <p>Le décompte s'appuie sur la date de dernière modification et non sur
     * la date de fin : cette dernière n'est pas toujours renseignée lorsqu'un
     * traitement échoue brutalement.</p>
     *
     * @return le nombre d'exécutions au statut {@code FAILED} ce jour,
     *         ou {@code 0} si l'agrégat n'a pu être calculé
     */
    public int countFailedToday() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM BATCH_JOB_EXECUTION WHERE STATUS = 'FAILED' AND CAST(LAST_UPDATED AS DATE) = CAST(GETDATE() AS DATE)",
                Integer.class);
        return count != null ? count : 0;
    }

    /**
     * Calcule le taux de succès sur une fenêtre glissante, soit
     * {@code COMPLETED / (COMPLETED + FAILED) × 100}.
     *
     * <p>Les exécutions encore en cours sont exclues du calcul. En l'absence
     * totale d'exécution terminée, la requête retourne 100 % : aucun échec
     * n'ayant été constaté, la période est considérée comme saine.</p>
     *
     * @param days profondeur de la fenêtre, en jours
     * @return le taux en pourcentage, ou {@code 0} si l'agrégat n'a pu être calculé
     */
    public double computeSuccessRate(int days) {
        Double rate = jdbc.queryForObject(
                """
                SELECT CASE WHEN COUNT(*) = 0 THEN 100.0
                            ELSE CAST(SUM(CASE WHEN STATUS = 'COMPLETED' THEN 1 ELSE 0 END) AS FLOAT)
                                 / COUNT(*) * 100.0
                       END
                FROM BATCH_JOB_EXECUTION
                WHERE STATUS IN ('COMPLETED','FAILED')
                  AND CREATE_TIME >= DATEADD(day, -?, GETDATE())
                """,
                Double.class, days);
        return rate != null ? rate : 0.0;
    }

    /**
     * Agrège les exécutions par traitement sur une fenêtre glissante.
     *
     * @param days profondeur de la fenêtre, en jours
     * @return un agrégat par traitement ayant au moins une exécution terminée
     *         sur la période
     */
    public List<JobAggregate> computeJobAggregates(int days) {
        return jdbc.query(
                """
                SELECT ji.JOB_NAME,
                       COUNT(*)                                              AS total_executions,
                       SUM(CASE WHEN je.STATUS='COMPLETED' THEN 1 ELSE 0 END) AS completed,
                       SUM(CASE WHEN je.STATUS='FAILED'    THEN 1 ELSE 0 END) AS failed,
                       AVG(DATEDIFF(second, je.START_TIME, je.END_TIME))     AS avg_duration_sec
                FROM BATCH_JOB_EXECUTION je
                INNER JOIN BATCH_JOB_INSTANCE ji ON ji.JOB_INSTANCE_ID = je.JOB_INSTANCE_ID
                WHERE je.STATUS IN ('COMPLETED','FAILED')
                  AND je.CREATE_TIME >= DATEADD(day, -?, GETDATE())
                GROUP BY ji.JOB_NAME
                """,
                (rs, row) -> new JobAggregate(
                        rs.getString("JOB_NAME"),
                        rs.getInt("total_executions"),
                        rs.getInt("completed"),
                        rs.getInt("failed"),
                        rs.getObject("avg_duration_sec") != null ? rs.getDouble("avg_duration_sec") : 0.0
                ),
                days);
    }

    /**
     * Agrège la volumétrie jour par jour sur une fenêtre glissante.
     *
     * <p>La jointure avec les étapes est nécessaire pour obtenir les
     * compteurs de lignes : une exécution sans étape n'apparaît donc pas dans
     * le résultat.</p>
     *
     * @param days profondeur de la fenêtre, en jours
     * @return un agrégat par journée, du plus récent au plus ancien
     */
    public List<DailyAggregate> computeDailyTrend(int days) {
        return jdbc.query(
                """
                SELECT CONVERT(varchar(8), je.CREATE_TIME, 112) AS exec_date,
                       SUM(se.READ_COUNT)                        AS total_read,
                       SUM(se.WRITE_COUNT)                       AS total_write,
                       COUNT(DISTINCT je.JOB_EXECUTION_ID)       AS job_count,
                       SUM(CASE WHEN je.STATUS='COMPLETED' THEN 1 ELSE 0 END) AS completed
                FROM BATCH_JOB_EXECUTION je
                INNER JOIN BATCH_STEP_EXECUTION se ON se.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID
                WHERE je.CREATE_TIME >= DATEADD(day, -?, GETDATE())
                GROUP BY CONVERT(varchar(8), je.CREATE_TIME, 112)
                ORDER BY exec_date DESC
                """,
                (rs, row) -> new DailyAggregate(
                        rs.getString("exec_date"),
                        rs.getLong("total_read"),
                        rs.getLong("total_write"),
                        rs.getInt("job_count"),
                        rs.getInt("completed")
                ),
                days);
    }

    /**
     * Recherche la dernière exécution réussie d'un traitement.
     *
     * <p>Sert à déterminer la date métier active et à évaluer le respect des
     * fenêtres SLA.</p>
     *
     * @param jobName nom logique du traitement
     * @return la dernière exécution {@code COMPLETED}, ou
     *         {@link Optional#empty()} si le traitement n'a jamais abouti
     */
    public Optional<BatchJobExecution> findLastSuccessfulByJobName(String jobName) {
        List<BatchJobExecution> result = jdbc.query(
                BASE_JOB_SELECT + """
             WHERE ji.JOB_NAME = ? AND je.STATUS = 'COMPLETED'
             ORDER BY je.END_TIME DESC
             OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY
            """,
                JOB_EXEC_MAPPER, jobName);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    /**
     * Compte les échecs d'un traitement parmi ses dernières exécutions.
     *
     * <p>La fenêtre d'observation est bornée aux dix dernières exécutions
     * terminées. Le décompte porte sur le nombre total d'échecs dans cette
     * fenêtre, sans exiger qu'ils se suivent : c'est ce compteur qui alimente
     * le seuil d'alerte de défaillance répétée.</p>
     *
     * @param jobName nom logique du traitement
     * @return le nombre d'échecs observés, entre {@code 0} et {@code 10}
     */
    public int countConsecutiveFailures(String jobName) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM (
                  SELECT TOP 10 je.STATUS
                  FROM BATCH_JOB_EXECUTION je
                  INNER JOIN BATCH_JOB_INSTANCE ji ON ji.JOB_INSTANCE_ID = je.JOB_INSTANCE_ID
                  WHERE ji.JOB_NAME = ? AND je.STATUS IN ('COMPLETED','FAILED')
                  ORDER BY je.CREATE_TIME DESC
                ) recent
                WHERE STATUS = 'FAILED'
                """,
                Integer.class, jobName);
        return count != null ? count : 0;
    }

    // ── Inner DTOs pour les agrégats ────────────────────────────────────────

    /**
     * Agrégat des exécutions d'un traitement sur la fenêtre analysée.
     *
     * @param jobName         nom logique du traitement
     * @param totalExecutions nombre d'exécutions terminées
     * @param completed       nombre d'exécutions réussies
     * @param failed          nombre d'exécutions en échec
     * @param avgDurationSec  durée moyenne en secondes, {@code 0} si non calculable
     */
    public record JobAggregate(
            String jobName,
            int totalExecutions,
            int completed,
            int failed,
            double avgDurationSec
    ) {
        /**
         * Taux de réussite du traitement sur la fenêtre.
         *
         * @return le taux en pourcentage, ou {@code 0} en l'absence d'exécution
         */
        public double successRate() {
            return totalExecutions == 0 ? 0.0 : (double) completed / totalExecutions * 100.0;
        }
    }

    /**
     * Volumétrie agrégée d'une journée d'extraction.
     *
     * @param date       journée concernée, au format compact {@code AAAAMMJJ}
     * @param totalRead  lignes lues depuis Oracle ce jour-là
     * @param totalWrite lignes écrites en SQL Server ce jour-là
     * @param jobCount   nombre d'exécutions distinctes
     * @param completed  nombre d'exécutions réussies
     */
    public record DailyAggregate(
            String date,
            long totalRead,
            long totalWrite,
            int jobCount,
            int completed
    ) {}

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Convertit un horodatage JDBC en date Java, en préservant l'absence de valeur.
     *
     * @param ts horodatage lu depuis la base, éventuellement {@code null}
     * @return la date correspondante, ou {@code null} si la colonne est vide
     */
    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }
}
