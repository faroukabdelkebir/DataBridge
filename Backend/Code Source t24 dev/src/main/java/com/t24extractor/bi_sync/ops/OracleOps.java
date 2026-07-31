package com.t24extractor.bi_sync.ops;

import com.t24extractor.model.SchemaSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;

import static com.t24extractor.commons.Constants.COUNT_ALL_ROWS_TPL;
import static com.t24extractor.commons.Constants.ORACLE_COUNT_FROM_STATS;
import static com.t24extractor.commons.Constants.ORACLE_COUNT_TOP_FIVE_TPL;

/**
 * Opérations sur la base source T24 (Oracle), strictement en lecture.
 *
 * <p>Seuls les comptages et l'échappement — sans effet, Oracle acceptant les
 * noms T24 tels quels — sont implémentés. Toute opération d'écriture lève
 * {@link UnsupportedOperationException} : c'est un garde-fou délibéré, la base
 * de production T24 ne devant jamais être modifiée par l'extracteur.
 *
 * <p>Le comptage privilégie les statistiques du dictionnaire Oracle
 * ({@code user_tab_partitions}, {@code user_tables}) et ne retombe sur un
 * {@code COUNT(1)} exact que si elles sont absentes. Un échec est propagé en
 * {@link IllegalStateException} : le nombre de lignes détermine le
 * partitionnement de lecture, et un zéro silencieux ferait conclure à tort que
 * la table est vide.
 */
@Slf4j
public class OracleOps extends SqlOps {

    @Override
    public Set<String> destColumns(JdbcTemplate jdbcTemplate, String table) {
        throw new UnsupportedOperationException("destColumns is not supported in OracleOps");
    }

    @Override
    public void createRawTable(JdbcTemplate jdbcTemplate, String table) {
        throw new UnsupportedOperationException("createRawTable is not supported in OracleOps");
    }

    @Override
    public String existsQuery(String table) {
        throw new UnsupportedOperationException("existsQuery is not supported in OracleOps");
    }

    @Override
    public void createDestTable(JdbcTemplate jdbcTemplate, String table, Set<SchemaSource> set) {
        throw new UnsupportedOperationException("createDestTable is not supported in OracleOps");
    }

    @Override
    public void createDefaultDestTable(JdbcTemplate jdbcTemplate, String table) {
        throw new UnsupportedOperationException("createDefaultDestTable is not supported in OracleOps");
    }

    @Override
    public String insertConstruction(String table, Set<SchemaSource> set) {
        throw new UnsupportedOperationException("insertConstruction is not supported in OracleOps");
    }

    @Override
    public String mergeConstruction(String table, Set<SchemaSource> set) {
        throw new UnsupportedOperationException("mergeConstruction is not supported in OracleOps");
    }

    @Override
    public void addCol(JdbcTemplate jdbcTemplate, String table, String column, String dataType) {
        throw new UnsupportedOperationException("addCol is not supported in OracleOps");
    }

    @Override
    public void dropCol(JdbcTemplate jdbcTemplate, String table, String col) {
        throw new UnsupportedOperationException("dropCol is not supported in OracleOps");
    }

    @Override
    public boolean existIndexQuery(JdbcTemplate jdbcTemplate, String tableName, String column) {
        throw new UnsupportedOperationException("existIndexQuery is not supported in OracleOps");
    }

    @Override
    public String escapeColumn(String column) {
        return column;
    }

    @Override
    public String escapeTableName(String table) {
        return table;
    }

    @Override
    public void createIndex(JdbcTemplate jdbcTemplate, String tableName, String column) {
        throw new UnsupportedOperationException("createIndex is not supported in OracleOps");
    }

    @Override
    public void dropIndex(JdbcTemplate jdbcTemplate, String column) {
        throw new UnsupportedOperationException("dropIndex is not supported in OracleOps");
    }

    @Override
    public int jsonNormalize(JdbcTemplate jdbcTemplate, String tableName) {
        throw new UnsupportedOperationException("jsonNormalize is not supported in OracleOps");
    }

    @Override
    public Long countTopFive(JdbcTemplate jdbcTemplate, String tableName) {
        Long countTop = 0L;
        try {
            countTop = jdbcTemplate.queryForObject(
                    String.format(ORACLE_COUNT_TOP_FIVE_TPL, tableName), Long.class);
        } catch (Exception e) {
            throw new IllegalStateException("Erreur COUNT TOP 5 sur la table " + tableName, e);
        }
        return countTop;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException si le comptage échoue — mieux vaut arrêter
     *         le job que dimensionner la lecture sur une valeur fausse
     */
    @Override
    public Long countAll(JdbcTemplate jdbcTemplate, String table) {
        try {
            Long metadataCount =
                    jdbcTemplate.queryForObject(ORACLE_COUNT_FROM_STATS, Long.class, table, table);
            if (metadataCount != null) {
                return metadataCount;
            }

            // Fallback exact count when Oracle stats are missing or not yet gathered.
            return jdbcTemplate.queryForObject(String.format(COUNT_ALL_ROWS_TPL, table), Long.class);
        } catch (Exception e) {
            log.error("Erreur lors du count sur la table {}", table, e);
            throw new IllegalStateException("Echec lors du count sur la table " + table, e);
        }
    }
}
