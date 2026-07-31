package wevioo.se.monitoringms.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import wevioo.se.monitoringms.model.SchemaAuditRecord;
import wevioo.se.monitoringms.model.SchemaSourceRecord;

import java.util.List;

/**
 * Accès en lecture au dictionnaire de schéma T24 et à son historique.
 *
 * <p>Interroge {@code SCHEMA_SOURCE}, qui décrit la correspondance entre
 * champs T24 et colonnes SQL Server, ainsi que {@code SCHEMA_SOURCE_AUDIT},
 * alimentée par un déclencheur à chaque modification du dictionnaire.</p>
 *
 * <p>Les noms de colonnes de ces tables mêlent plusieurs conventions
 * ({@code _enabled}, {@code ssTag}, {@code UpdatedOn}…) : les requêtes
 * énumèrent donc explicitement les colonnes plutôt que d'utiliser {@code *}.</p>
 *
 * @see wevioo.se.monitoringms.model.SchemaSourceRecord
 * @see wevioo.se.monitoringms.model.SchemaAuditRecord
 */
@Repository
@RequiredArgsConstructor
public class SchemaRepository {

    /** Gabarit JDBC pointant sur la base décisionnelle SQL Server. */
    private final JdbcTemplate jdbc;

    /** Convertit une ligne de {@code SCHEMA_SOURCE} en modèle. */
    private static final RowMapper<SchemaSourceRecord> SOURCE_MAPPER = (rs, row) ->
            SchemaSourceRecord.builder()
                    .id(rs.getString("id"))
                    .tableName(rs.getString("table_name"))
                    .columnName(rs.getString("column_name"))
                    .dataType(rs.getString("datatype"))
                    .enabled(rs.getBoolean("_enabled"))
                    .indexed(rs.getBoolean("indexed"))
                    .ssTag(rs.getString("ssTag"))
                    .singleMult(rs.getString("single_mult"))
                    .localRef(rs.getBoolean("local_ref"))
                    .insertDate(rs.getObject("insert_date", Long.class))
                    .build();

    /** Convertit une ligne de {@code SCHEMA_SOURCE_AUDIT} en modèle. */
    private static final RowMapper<SchemaAuditRecord> AUDIT_MAPPER = (rs, row) ->
            SchemaAuditRecord.builder()
                    .id(rs.getInt("id"))
                    .sourceId(rs.getString("sourceId"))
                    .sourceTableName(rs.getString("sourceTable_name"))
                    .sourceColumnName(rs.getString("sourceColumn_name"))
                    .sourceDataType(rs.getString("sourceDatatype"))
                    .newSourceEnabled(rs.getInt("newSource_enabled"))
                    .oldSourceEnabled(rs.getInt("oldSource_enabled"))
                    .newSourceIndexed(rs.getInt("newSourceIndexed"))
                    .oldSourceIndexed(rs.getInt("oldSourceIndexed"))
                    .updatedBy(rs.getString("UpdatedBy"))
                    .updatedOn(rs.getObject("UpdatedOn", Long.class))
                    .build();

    // ── SCHEMA_SOURCE ────────────────────────────────────────────────────────

    /**
     * Charge l'intégralité du dictionnaire de schéma.
     *
     * @return toutes les colonnes connues, triées par table puis par colonne
     */
    public List<SchemaSourceRecord> findAll() {
        return jdbc.query(
                "SELECT id, table_name, column_name, datatype, _enabled, indexed, ssTag, single_mult, local_ref, insert_date FROM SCHEMA_SOURCE ORDER BY table_name, column_name",
                SOURCE_MAPPER);
    }

    /**
     * Charge les colonnes déclarées pour une table T24.
     *
     * @param tableName nom de la table T24, par exemple {@code FUNDS_TRANSFER#HIS}
     * @return les colonnes de la table, triées par nom ; liste vide si la table
     *         est absente du dictionnaire
     */
    public List<SchemaSourceRecord> findByTable(String tableName) {
        return jdbc.query(
                "SELECT id, table_name, column_name, datatype, _enabled, indexed, ssTag, single_mult, local_ref, insert_date FROM SCHEMA_SOURCE WHERE table_name = ? ORDER BY column_name",
                SOURCE_MAPPER, tableName);
    }

    /**
     * Charge les seules colonnes effectivement extraites d'une table T24.
     *
     * @param tableName nom de la table T24
     * @return les colonnes activées, triées par nom ; liste vide si aucune ne
     *         l'est
     */
    public List<SchemaSourceRecord> findEnabled(String tableName) {
        return jdbc.query(
                "SELECT id, table_name, column_name, datatype, _enabled, indexed, ssTag, single_mult, local_ref, insert_date FROM SCHEMA_SOURCE WHERE table_name = ? AND _enabled = 1 ORDER BY column_name",
                SOURCE_MAPPER, tableName);
    }

    /**
     * Résume la couverture du dictionnaire, table par table.
     *
     * @return un résumé par table T24, trié par nom de table
     */
    public List<TableSchemaSummary> summarizeByTable() {
        return jdbc.query(
                """
                SELECT table_name,
                       COUNT(*)                                         AS total_columns,
                       SUM(CAST(_enabled AS int))                       AS enabled_columns,
                       SUM(CAST(indexed AS int))                        AS indexed_columns,
                       SUM(CASE WHEN single_mult = 'M' THEN 1 ELSE 0 END) AS mv_columns
                FROM SCHEMA_SOURCE
                GROUP BY table_name
                ORDER BY table_name
                """,
                (rs, row) -> new TableSchemaSummary(
                        rs.getString("table_name"),
                        rs.getInt("total_columns"),
                        rs.getInt("enabled_columns"),
                        rs.getInt("indexed_columns"),
                        rs.getInt("mv_columns")
                ));
    }

    // ── SCHEMA_SOURCE_AUDIT ──────────────────────────────────────────────────

    /**
     * Charge les dernières modifications de schéma, toutes tables confondues.
     *
     * @param limit nombre maximal d'événements à ramener
     * @return les événements du plus récent au plus ancien
     */
    public List<SchemaAuditRecord> findRecentAudit(int limit) {
        return jdbc.query(
                "SELECT TOP (?) id, sourceId, sourceTable_name, sourceColumn_name, sourceDatatype, newSource_enabled, oldSource_enabled, newSourceIndexed, oldSourceIndexed, UpdatedBy, UpdatedOn FROM SCHEMA_SOURCE_AUDIT ORDER BY UpdatedOn DESC",
                AUDIT_MAPPER, limit);
    }

    /**
     * Charge l'historique des modifications de schéma d'une table.
     *
     * @param tableName nom de la table T24
     * @return les événements du plus récent au plus ancien ; liste vide si la
     *         table n'a jamais été modifiée
     */
    public List<SchemaAuditRecord> findAuditByTable(String tableName) {
        return jdbc.query(
                "SELECT id, sourceId, sourceTable_name, sourceColumn_name, sourceDatatype, newSource_enabled, oldSource_enabled, newSourceIndexed, oldSourceIndexed, UpdatedBy, UpdatedOn FROM SCHEMA_SOURCE_AUDIT WHERE sourceTable_name = ? ORDER BY UpdatedOn DESC",
                AUDIT_MAPPER, tableName);
    }

    /**
     * Compte les dérives de schéma survenues récemment.
     *
     * <p>Toute modification enregistrée par le déclencheur d'audit compte,
     * qu'il s'agisse d'une activation, d'une désactivation ou d'une pose
     * d'index. L'horodatage étant stocké sous forme numérique
     * {@code AAAAMMJJHHMMSS}, la borne est convertie dans ce format.</p>
     *
     * @param days profondeur de la fenêtre, en jours
     * @return le nombre de modifications sur la période, ou {@code 0} si
     *         l'agrégat n'a pu être calculé
     */
    public int countRecentDrifts(int days) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM SCHEMA_SOURCE_AUDIT
                WHERE UpdatedOn >= CAST(FORMAT(DATEADD(day, -?, GETDATE()), 'yyyyMMddHHmmss') AS bigint)
                """,
                Integer.class, days);
        return count != null ? count : 0;
    }

    // ── Inner record ────────────────────────────────────────────────────────

    /**
     * Couverture du dictionnaire pour une table T24.
     *
     * @param tableName      nom de la table T24
     * @param totalColumns   nombre de colonnes déclarées
     * @param enabledColumns nombre de colonnes effectivement extraites
     * @param indexedColumns nombre de colonnes indexées côté SQL Server
     * @param mvColumns      nombre de colonnes multivaluées
     */
    public record TableSchemaSummary(
            String tableName,
            int    totalColumns,
            int    enabledColumns,
            int    indexedColumns,
            int    mvColumns
    ) {}
}
