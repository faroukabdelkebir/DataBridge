package com.t24extractor.bi_sync.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.t24extractor.model.SchemaSource;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Oracle est la source (lecture seule) : seuls les comptages et l'échappement
 * sont supportés. Ce test fige ce contrat pour éviter qu'une écriture Oracle
 * soit branchée par erreur.
 */
class OracleOpsTest {

    private final OracleOps ops = new OracleOps();
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    @Test
    void countsFromPartitionOrTableStatistics() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("FBNK_CUSTOMER"), eq("FBNK_CUSTOMER")))
                .thenReturn(4200L);

        assertEquals(4200L, ops.countAll(jdbcTemplate, "FBNK_CUSTOMER"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Long.class), eq("FBNK_CUSTOMER"), eq("FBNK_CUSTOMER"));
        assertEquals(true, sql.getValue().contains("user_tab_partitions"));
        assertEquals(true, sql.getValue().contains("user_tables"));
    }

    @Test
    void fallsBackToExactCountWhenStatisticsAreMissing() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("FBNK_CUSTOMER"), eq("FBNK_CUSTOMER")))
                .thenReturn(null);
        when(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM FBNK_CUSTOMER", Long.class))
                .thenReturn(17L);

        assertEquals(17L, ops.countAll(jdbcTemplate, "FBNK_CUSTOMER"));
    }

    @Test
    void wrapsCountFailuresInIllegalStateException() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("BROKEN"), eq("BROKEN")))
                .thenThrow(new RuntimeException("ORA-00942"));

        // Contrairement à SQL Server, un échec de count Oracle doit faire échouer le job :
        // un 0 silencieux ferait croire à une table vide et déclencherait une purge BI.
        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> ops.countAll(jdbcTemplate, "BROKEN"));
        assertEquals("Echec lors du count sur la table BROKEN", error.getMessage());
    }

    @Test
    void countsTopFiveWithOracleFetchSyntax() {
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (SELECT 1 FROM FBNK_CUSTOMER FETCH FIRST 5 ROWS ONLY)", Long.class))
                .thenReturn(5L);

        assertEquals(5L, ops.countTopFive(jdbcTemplate, "FBNK_CUSTOMER"));
    }

    @Test
    void wrapsTopFiveFailures() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(new RuntimeException("ORA-00942"));

        assertThrows(IllegalStateException.class, () -> ops.countTopFive(jdbcTemplate, "BROKEN"));
    }

    @Test
    void doesNotEscapeIdentifiers() {
        assertEquals("FBNK_CUSTOMER", ops.escapeTableName("FBNK_CUSTOMER"));
        assertEquals("RECID", ops.escapeColumn("RECID"));
    }

    @Test
    void rejectsEveryWriteOperation() {
        Set<SchemaSource> empty = new LinkedHashSet<>();

        assertUnsupported(() -> ops.destColumns(jdbcTemplate, "T"));
        assertUnsupported(() -> ops.createRawTable(jdbcTemplate, "T"));
        assertUnsupported(() -> ops.existsQuery("T"));
        assertUnsupported(() -> ops.createDestTable(jdbcTemplate, "T", empty));
        assertUnsupported(() -> ops.createDefaultDestTable(jdbcTemplate, "T"));
        assertUnsupported(() -> ops.insertConstruction("T", empty));
        assertUnsupported(() -> ops.mergeConstruction("T", empty));
        assertUnsupported(() -> ops.addCol(jdbcTemplate, "T", "C", "String"));
        assertUnsupported(() -> ops.dropCol(jdbcTemplate, "T", "C"));
        assertUnsupported(() -> ops.existIndexQuery(jdbcTemplate, "T", "C"));
        assertUnsupported(() -> ops.createIndex(jdbcTemplate, "T", "C"));
        assertUnsupported(() -> ops.dropIndex(jdbcTemplate, "C"));
        assertUnsupported(() -> ops.jsonNormalize(jdbcTemplate, "T"));
    }

    private void assertUnsupported(Executable operation) {
        assertThrows(UnsupportedOperationException.class, operation);
    }
}
