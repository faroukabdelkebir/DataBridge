package wevioo.se.monitoringms.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import wevioo.se.monitoringms.model.StatsRecord;

import java.util.List;

/**
 * Accès en lecture à la table {@code stats}, alimentée par le microservice
 * d'extraction T24 à l'issue de chaque chargement.
 *
 * <p>Support de la réconciliation Oracle ↔ SQL Server et du calcul des
 * volumétries. La colonne portant le nombre de lignes chargées se nomme
 * {@code mongo_count}, héritage d'une cible NoSQL antérieure ; elle désigne
 * aujourd'hui les lignes insérées en SQL Server.</p>
 *
 * @see wevioo.se.monitoringms.model.StatsRecord
 * @see wevioo.se.monitoringms.service.ReconciliationService
 */
@Repository
@RequiredArgsConstructor
public class StatsRepository {

    /** Gabarit JDBC pointant sur la base décisionnelle SQL Server. */
    private final JdbcTemplate jdbc;

    /** Convertit une ligne de la table {@code stats} en modèle. */
    private static final RowMapper<StatsRecord> STATS_MAPPER = (rs, row) ->
            StatsRecord.builder()
                    .jobExecutionId(rs.getLong("job_execution_id"))
                    .mssqlTable(rs.getString("mssql_table"))
                    .businessDate(rs.getInt("business_date"))
                    .oracleCount(rs.getInt("oracle_count"))
                    .oracleChunkCount(rs.getInt("oracle_chunk_count"))
                    .sqlCount(rs.getInt("sql_count"))
                    .insertDate(rs.getLong("insert_date"))
                    .build();

    /**
     * Recherche le dernier comptage connu pour chaque table extraite.
     *
     * <p>La sélection retient, par table, la ligne dont la date d'insertion
     * est la plus récente.</p>
     *
     * @return un enregistrement par table, triés par nom de table
     */
    public List<StatsRecord> findLatestPerTable() {
        return jdbc.query(
                """
                SELECT s.job_execution_id, s.mssql_table, s.business_date,
                       s.oracle_count, s.oracle_chunk_count, s.mongo_count, s.insert_date
                FROM stats s
                INNER JOIN (
                    SELECT mssql_table, MAX(insert_date) AS max_date
                    FROM stats
                    GROUP BY mssql_table
                ) latest ON s.mssql_table = latest.mssql_table AND s.insert_date = latest.max_date
                ORDER BY s.mssql_table
                """,
                STATS_MAPPER);
    }

    /**
     * Recherche l'historique des comptages d'une table.
     *
     * @param tableName nom de la table SQL Server
     * @param limit     nombre maximal de lignes à ramener
     * @return les comptages du plus récent au plus ancien ; liste vide si la
     *         table est inconnue
     */
    public List<StatsRecord> findByTable(String tableName, int limit) {
        return jdbc.query(
                "SELECT job_execution_id, mssql_table, business_date, oracle_count, oracle_chunk_count, mongo_count, insert_date " +
                        "FROM stats WHERE mssql_table = ? ORDER BY insert_date DESC OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY",
                STATS_MAPPER, tableName, limit);
    }

    /**
     * Totalise les lignes chargées sur une fenêtre glissante, toutes tables
     * confondues.
     *
     * <p>Le filtrage porte sur la date métier, stockée sous forme numérique
     * {@code AAAAMMJJ} et convertie en date par la requête.</p>
     *
     * @param days profondeur de la fenêtre, en jours
     * @return le total des lignes chargées, ou {@code 0} en l'absence de données
     */
    public long sumRowsExtracted(int days) {
        Long total = jdbc.queryForObject(
                """
                SELECT ISNULL(SUM(mongo_count), 0)
                FROM stats
                WHERE CAST(CAST(business_date AS varchar(8)) AS DATE) >= DATEADD(day, -?, CAST(GETDATE() AS DATE))
                """,
                Long.class, days);
        return total != null ? total : 0L;
    }

    /**
     * Totalise les lignes chargées par table sur une fenêtre glissante.
     *
     * @param days profondeur de la fenêtre, en jours
     * @return un agrégat par table, de la plus volumineuse à la moins volumineuse
     */
    public List<TableVolumeRecord> sumRowsByTable(int days) {
        return jdbc.query(
                """
                SELECT mssql_table,
                       SUM(mongo_count)   AS total_loaded,
                       SUM(oracle_count)  AS total_source,
                       COUNT(*)           AS run_count
                FROM stats
                WHERE CAST(CAST(business_date AS varchar(8)) AS DATE) >= DATEADD(day, -?, CAST(GETDATE() AS DATE))
                GROUP BY mssql_table
                ORDER BY total_loaded DESC
                """,
                (rs, row) -> new TableVolumeRecord(
                        rs.getString("mssql_table"),
                        rs.getLong("total_loaded"),
                        rs.getLong("total_source"),
                        rs.getInt("run_count")
                ),
                days);
    }

    /**
     * Volumétrie cumulée d'une table sur la fenêtre analysée.
     *
     * @param tableName   nom de la table SQL Server
     * @param totalLoaded lignes effectivement chargées en cible
     * @param totalSource lignes comptées côté Oracle
     * @param runCount    nombre de chargements réalisés sur la période
     */
    public record TableVolumeRecord(
            String tableName,
            long totalLoaded,
            long totalSource,
            int  runCount
    ) {}
}
