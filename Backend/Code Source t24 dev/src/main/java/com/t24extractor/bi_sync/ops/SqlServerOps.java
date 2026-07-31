package com.t24extractor.bi_sync.ops;

import com.t24extractor.model.SchemaSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;

import java.sql.Types;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.t24extractor.commons.Constants.*;

/**
 * Génération SQL pour la base BI (SQL Server), seule base écrite.
 *
 * <p>Les identifiants sont délimités par des crochets, le nommage T24 étant
 * incompatible avec les règles d'identifiant non échappé.
 *
 * <p>Toute table cible reçoit trois colonnes techniques : {@code _id} (le RECID
 * T24), {@code ORIGINAL_ID} (pour regrouper les versions d'un enregistrement
 * historisé) et {@code INSERT_DATE}, horodatage indexé qui permet aux
 * consommateurs BI de ne relire que le dernier chargement.
 *
 * <p>Les comptages s'appuient sur {@code sys.dm_db_partition_stats} plutôt que
 * sur un {@code COUNT(*)}, prohibitif sur les tables volumineuses. En cas
 * d'échec ils renvoient {@code 0} sans lever — contrairement à
 * {@link OracleOps}, où le comptage conditionne le partitionnement.
 */
@Slf4j
public class SqlServerOps extends SqlOps {

    @Override
    public void createDestTable(JdbcTemplate jdbcTemplate, String table, Set<SchemaSource> set) {

        String fields = String.join(", \n", fieldsOnly(set));
        if (!fieldsOnly(set).isEmpty()) {
            fields = ", "+fields;
        }
        jdbcTemplate.batchUpdate(String.format(CREATE_DEST_TABLE_TPL, table, fields));
    }

    @Override
    public void createDefaultDestTable(JdbcTemplate jdbcTemplate, String table) {
        jdbcTemplate.batchUpdate(String.format(CREATE_DEFAULT_DEST_TABLE_TPL, table));
    }

    @Override
    public void createRawTable(JdbcTemplate jdbcTemplate, String table) {
        jdbcTemplate.execute(String.format(CREATE_RAW_TABLE_TPL, RAW_PREFIX + table));
    }


    @Override
    public String insertConstruction(String table, Set<SchemaSource> set) {
        return String.format(INSERT_INTO_TPL,
                escapeTableName(table),
                String.join(",", mssqlfieldsToInsert(set)),
                placeHolders(set.size() + 2));
    }

    @Override
    public String mergeConstruction(String table, Set<SchemaSource> set) {

        String idCol = escapeColumn(ID);

        // Colonnes reportées de la table de travail vers la table cible
        String allColSelect = set.stream()
                .map(colName -> String.format("trg.%s = src.%s", escapeColumn(colName.getColumnName()), escapeColumn(colName.getColumnName()))).collect(Collectors.joining(", "));

        String sql = String.format(MERGE_INTO_TPL,
                escapeTableName(table), escapeTableName(table), idCol, idCol, allColSelect);

        log.info("Merging staging table and sink table with this command: {}", sql);

        return sql;
    }

    @Override
    public void addCol(JdbcTemplate jdbcTemplate, String table, String column, String dataType) {
        String sql = existsQuery(table)
                + String.format(ALTER_TABLE_ADD_COLUMN_TPL, table, escapeColumn(column), getDataType(dataType));
        jdbcTemplate.batchUpdate(sql);
    }

    @Override
    public void dropCol(JdbcTemplate jdbcTemplate, String table, String field) {
        String sql = existsQuery(table)
                + String.format(ALTER_TABLE_DROP_COLUMN_TPL, table, field);
        jdbcTemplate.batchUpdate(sql);
    }

    @Override
    public Set<String> destColumns(JdbcTemplate jdbcTemplate, String table) {
        String sql = String.format(SELECT_DEST_COLUMNS_TPL, table, INSERT_DATE_COLUMN);

        return new HashSet<>(jdbcTemplate.queryForList(sql, String.class));
    }


    @Override
    public String existsQuery(String table) {
        return String.format(IF_EXISTS_TABLE_TPL, table);
    }

    @Override
    public boolean existIndexQuery(JdbcTemplate jdbcTemplate, String tableName, String column) {
        List<Map<String, Object>> index =
                jdbcTemplate.queryForList(String.format(SELECT_INDEX_TPL, column, tableName));

        return index.isEmpty();
    }

    @Override
    public String escapeColumn(String column) {
        return "[" + column + "]";
    }

    @Override
    public String escapeTableName(String table) {
        return "[" + table + "]";
    }

    @Override
    public void createIndex(JdbcTemplate jdbcTemplate, String tableName, String column) {
        if (existIndexQuery(jdbcTemplate, tableName, column)) {
            jdbcTemplate.execute(String.format(CREATE_INDEX_TPL, escapeColumn(column), escapeTableName(tableName)));
        }
    }

    @Override
    public void dropIndex(JdbcTemplate jdbcTemplate, String column) {
        jdbcTemplate.execute(String.format(DROP_INDEX_TPL, escapeColumn(column)));
    }

    @Override
    public int jsonNormalize(JdbcTemplate jdbcTemplate, String tableName) {
        SqlParameterSource in = new MapSqlParameterSource()
                .addValue("@tableName", tableName, Types.NVARCHAR)
                .addValue("rows_count", Types.INTEGER);
        SimpleJdbcCall simpleJdbcCall = new SimpleJdbcCall(jdbcTemplate);
        Map<String, Object> result = simpleJdbcCall.withProcedureName(JSON_NORMALIZATION_PROCEDURE)
                .withSchemaName(DBO_SCHEMA)
                .declareParameters(new SqlParameter("@tableName", Types.NVARCHAR))
                .execute(in);
        return (int) result.get("rows_count");
    }

    @Override
    public Long countTopFive(JdbcTemplate jdbcTemplate, String table){
        Long countTop = 0L;
        try{
            countTop = jdbcTemplate.queryForObject(String.format(SQLSERVER_COUNT_TOP_FIVE_TPL, table), Long.class);
        }catch (Exception e){
            log.error("Une erreur est survenue lors de l'exécution de la requete Count sur la table {}", table);
        }
        return countTop;
    }

    @Override
    public Long countAll(JdbcTemplate jdbcTemplate, String table) {
        Long count = 0L;
        try {
            count = jdbcTemplate.queryForObject(String.format(SQLSERVER_COUNT_FROM_STATS_TPL, table), Long.class);
        } catch (Exception e) {
            log.error("Une erreur est survenue lors de l'exécution de la requete Count sur la table {}", table);
        }
        return count;
    }


}
