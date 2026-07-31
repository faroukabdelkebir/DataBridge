package com.t24extractor.bi_sync.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.t24extractor.model.MetaDataTable;
import com.t24extractor.model.SchemaSource;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tests de non-régression sur le SQL généré pour SQL Server.
 *
 * Le SQL est ici un livrable : toute dérive (échappement, ordre des colonnes,
 * colonnes obligatoires, index) casse silencieusement l'alimentation BI. Ces
 * tests figent donc les chaînes produites plutôt que de vérifier un simple
 * "contient".
 */
class SqlServerOpsTest {

    private final SqlServerOps ops = new SqlServerOps();
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    // ── DDL ──────────────────────────────────────────────────────────────────

    @Test
    void createsDestinationTableWithMandatoryColumnsAndInsertDateIndex() {
        ops.createDestTable(jdbcTemplate, "CUSTOMER", schemas("NAME", "COMMENTS"));

        String sql = captureBatchUpdate();
        assertTrue(sql.startsWith("IF NOT EXISTS (SELECT * FROM sys.tables WHERE name='CUSTOMER') "
                + "CREATE TABLE [CUSTOMER]("), sql);
        assertTrue(sql.contains("[_id]NVARCHAR(255) NOT NULL"), sql);
        assertTrue(sql.contains("[ORIGINAL_ID]NVARCHAR(250) not null"), sql);
        assertTrue(sql.contains("[INSERT_DATE] BIGINT default"), sql);
        assertTrue(sql.contains("[NAME] VARCHAR(8000)"), sql);
        assertTrue(sql.contains("INDEX ins_date_idx NONCLUSTERED ([INSERT_DATE])"), sql);
    }

    @Test
    void createsDestinationTableWithoutTrailingCommaWhenNoBusinessColumn() {
        ops.createDestTable(jdbcTemplate, "CUSTOMER", new LinkedHashSet<>());

        String sql = captureBatchUpdate();
        // Sans colonne métier, aucune virgule parasite ne doit précéder l'index.
        assertFalse(sql.contains(",,"), sql);
        assertTrue(sql.contains("INDEX ins_date_idx NONCLUSTERED ([INSERT_DATE])"), sql);
    }

    @Test
    void createsDefaultDestinationTableWithOnlyMandatoryColumns() {
        ops.createDefaultDestTable(jdbcTemplate, "CUSTOMER");

        String sql = captureBatchUpdate();
        assertTrue(sql.contains("CREATE TABLE [CUSTOMER]("), sql);
        assertTrue(sql.contains("[_id]NVARCHAR(255) NOT NULL"), sql);
        assertTrue(sql.contains("[ORIGINAL_ID]NVARCHAR(250) not null"), sql);
        assertFalse(sql.contains("[NAME]"), sql);
    }

    @Test
    void createsRawTableWithRawPrefix() {
        ops.createRawTable(jdbcTemplate, "CUSTOMER");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(sql.capture());
        assertEquals("IF NOT EXISTS (SELECT * FROM sys.tables WHERE name='RAW_CUSTOMER') "
                + "CREATE TABLE [RAW_CUSTOMER](raw_id   NVARCHAR(255),\nraw_data VARCHAR(MAX))",
                sql.getValue());
    }

    // ── DML ──────────────────────────────────────────────────────────────────

    @Test
    void buildsInsertWithMandatoryColumnsFirstAndMatchingPlaceholderCount() {
        Set<SchemaSource> fields = schemas("NAME", "COMMENTS");
        String sql = ops.insertConstruction("CUSTOMER", fields);

        assertEquals("INSERT INTO [CUSTOMER]([_id],[ORIGINAL_ID],[NAME],[COMMENTS]) VALUES (?,?,?,?);", sql);
        // Autant de placeholders que de colonnes : garde-fou contre un décalage d'insertion.
        assertEquals(ops.mssqlfieldsToInsert(fields).size(), countOccurrences(sql, "?"));
    }

    @Test
    void buildsInsertForEmptySchemaWithOnlyMandatoryColumns() {
        assertEquals("INSERT INTO [CUSTOMER]([_id],[ORIGINAL_ID]) VALUES (?,?);",
                ops.insertConstruction("CUSTOMER", new LinkedHashSet<>()));
    }

    @Test
    void buildsMergeMatchingOnIdAndUpdatingEveryColumn() {
        String sql = ops.mergeConstruction("CUSTOMER", schemas("NAME", "COMMENTS"));

        assertEquals("MERGE INTO [CUSTOMER] trg USING (SELECT * FROM [CUSTOMER] ) src ON "
                + " (src.[_id]= trg.[_id] ) "
                + "trg.[NAME] = src.[NAME], trg.[COMMENTS] = src.[COMMENTS] ); ", sql);
    }

    @Test
    void addsColumnGuardedByTableExistenceCheck() {
        ops.addCol(jdbcTemplate, "CUSTOMER", "NICKNAME", "String");

        assertEquals("IF EXISTS (SELECT * FROM sys.tables WHERE name ='CUSTOMER')"
                + "ALTER TABLE CUSTOMER\nADD [NICKNAME] VARCHAR(8000)", captureBatchUpdate());
    }

    @Test
    void addsMultiValueColumnAsVarcharMax() {
        ops.addCol(jdbcTemplate, "CUSTOMER", "PHONES", "StringMAX");

        assertTrue(captureBatchUpdate().endsWith("ADD [PHONES]  VARCHAR(MAX) "));
    }

    @Test
    void dropsColumnGuardedByTableExistenceCheck() {
        ops.dropCol(jdbcTemplate, "CUSTOMER", "NICKNAME");

        assertEquals("IF EXISTS (SELECT * FROM sys.tables WHERE name ='CUSTOMER')"
                + "\n ALTER TABLE CUSTOMER \n DROP COLUMN NICKNAME", captureBatchUpdate());
    }

    @Test
    void listsDestinationColumnsExcludingTechnicalOnes() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of("NAME", "COMMENTS"));

        assertEquals(Set.of("NAME", "COMMENTS"), ops.destColumns(jdbcTemplate, "CUSTOMER"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture(), eq(String.class));
        assertTrue(sql.getValue().contains("COLUMN_NAME NOT IN ('_id', 'ORIGINAL_ID', 'INSERT_DATE')"),
                sql.getValue());
    }

    @Test
    void buildsUpdateStatementWithAllColumnsAndCondition() {
        Map<String, Object> cols = new LinkedHashMap<>();
        cols.put("last_date", "'20260101'");
        cols.put("enabled", 1);

        ops.updateQuery(jdbcTemplate, "BATCH_CONFIG", cols, "id = '1'");

        assertEquals("UPDATE BATCH_CONFIG\n SET last_date='20260101', enabled=1\n WHERE id = '1'",
                captureBatchUpdate());
    }

    @Test
    void savesMetadataOnlyWhenNotAlreadyPresent() {
        ops.save(jdbcTemplate, List.of(new MetaDataTable("CUSTOMER", "PHONES", true)));

        String sql = captureBatchUpdate();
        assertTrue(sql.startsWith("IF NOT EXISTS(SELECT * from T24_Metadata Where "
                + "[Table_Name]='CUSTOMER' and MultiValueFields='PHONES')"), sql);
        assertTrue(sql.contains("INSERT INTO T24_Metadata VALUES ('CUSTOMER','PHONES','true')"), sql);
    }

    // ── Index ────────────────────────────────────────────────────────────────

    @Test
    void createsIndexOnlyWhenAbsent() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        ops.createIndex(jdbcTemplate, "CUSTOMER", "DATE_TIME");

        verify(jdbcTemplate).execute("CREATE NONCLUSTERED INDEX [DATE_TIME] ON [CUSTOMER]([DATE_TIME])");
    }

    @Test
    void skipsIndexCreationWhenAlreadyPresent() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(Map.of("name", "DATE_TIME")));

        ops.createIndex(jdbcTemplate, "CUSTOMER", "DATE_TIME");

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void dropsIndexByEscapedName() {
        ops.dropIndex(jdbcTemplate, "DATE_TIME");

        verify(jdbcTemplate).execute("DROP INDEX [DATE_TIME]");
    }

    // ── Comptages ────────────────────────────────────────────────────────────

    @Test
    void countsAllRowsFromPartitionStatistics() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(1234L);

        assertEquals(1234L, ops.countAll(jdbcTemplate, "CUSTOMER"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Long.class));
        assertTrue(sql.getValue().contains("sys.dm_db_partition_stats"), sql.getValue());
        assertTrue(sql.getValue().contains("object_name(object_id) = 'CUSTOMER'"), sql.getValue());
    }

    @Test
    void countsFallBackToZeroInsteadOfPropagatingSqlServerFailures() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenThrow(new RuntimeException("db unavailable"));

        // Contrairement à Oracle, l'implémentation SQL Server dégrade en 0 sans lever.
        assertEquals(0L, ops.countAll(jdbcTemplate, "CUSTOMER"));
        assertEquals(0L, ops.countTopFive(jdbcTemplate, "CUSTOMER"));
    }

    @Test
    void countsTopFiveRows() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(5L);

        assertEquals(5L, ops.countTopFive(jdbcTemplate, "CUSTOMER"));
    }

    // ── Helpers partagés de SqlOps ───────────────────────────────────────────

    @Test
    void buildsFieldDefinitionsAndInsertFieldLists() {
        Set<SchemaSource> set = new LinkedHashSet<>();
        set.add(schema("NAME", "String"));
        set.add(schema("PHONES", "StringMAX"));

        assertEquals(List.of("[NAME] VARCHAR(8000)", "[PHONES]  VARCHAR(MAX) "),
                List.copyOf(ops.fieldsOnly(set)));
        assertEquals(List.of("_id", "ORIGINAL_ID", "NAME", "PHONES"), ops.fieldsToInsert(set));
        assertEquals(List.of("[_id]", "[ORIGINAL_ID]", "[NAME]", "[PHONES]"), ops.mssqlfieldsToInsert(set));
    }

    @Test
    void buildsPlaceholdersDefensivelyForEmptyAndSingleColumn() {
        assertEquals("?", ops.placeHolders(1));
        assertEquals("?", ops.placeHolders(0));
        assertEquals("?,?,?,?,?", ops.placeHolders(5));
    }

    @Test
    void escapesIdentifiersWithSquareBrackets() {
        assertEquals("[CUSTOMER]", ops.escapeTableName("CUSTOMER"));
        assertEquals("[DATE_TIME]", ops.escapeColumn("DATE_TIME"));
        assertEquals("IF EXISTS (SELECT * FROM sys.tables WHERE name ='CUSTOMER')",
                ops.existsQuery("CUSTOMER"));
    }

    @Test
    void truncatesAndDropsTables() {
        ops.truncateTable(jdbcTemplate, "CUSTOMER");
        ops.dropTable(jdbcTemplate, "CUSTOMER");

        verify(jdbcTemplate).execute("TRUNCATE TABLE [CUSTOMER]");
        verify(jdbcTemplate).execute("DROP TABLE [CUSTOMER]");
    }

    // ── Utilitaires de test ──────────────────────────────────────────────────

    private String captureBatchUpdate() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).batchUpdate(sql.capture());
        return sql.getValue();
    }

    private Set<SchemaSource> schemas(String... columns) {
        Set<SchemaSource> set = new LinkedHashSet<>();
        for (String column : columns) {
            set.add(schema(column, "String"));
        }
        return set;
    }

    private SchemaSource schema(String column, String dataType) {
        return new SchemaSource("id-" + column, "CUSTOMER", column, dataType,
                true, false, "c1", "S", false);
    }

    private long countOccurrences(String value, String token) {
        return value.split(java.util.regex.Pattern.quote(token), -1).length - 1L;
    }
}
