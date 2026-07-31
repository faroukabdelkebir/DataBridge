package com.t24extractor.bi_sync.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.t24extractor.model.SchemaSource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SqlOpsTest {

    private final SqlServerOps sqlOps = new SqlServerOps();

    @Test
    void buildsInsertStatementWithEscapedColumns() {
        Set<SchemaSource> fields = new LinkedHashSet<>();
        fields.add(schema("NAME", "String"));
        fields.add(schema("COMMENTS", "StringMAX"));

        assertEquals(
                "INSERT INTO [CUSTOMER]([_id],[ORIGINAL_ID],[NAME],[COMMENTS]) VALUES (?,?,?,?);",
                sqlOps.insertConstruction("CUSTOMER", fields));
    }

    @Test
    void mapsSchemaTypesAndPlaceholders() {
        assertEquals(" VARCHAR(MAX) ", sqlOps.getDataType("StringMAX"));
        assertEquals("VARCHAR(8000)", sqlOps.getDataType("String"));
        assertEquals("?,?,?", sqlOps.placeHolders(3));
    }

    @Test
    void escapesAndExecutesTableOperations() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        sqlOps.truncateTable(jdbcTemplate, "CUSTOMER");
        sqlOps.dropTable(jdbcTemplate, "CUSTOMER");

        verify(jdbcTemplate).execute("TRUNCATE TABLE [CUSTOMER]");
        verify(jdbcTemplate).execute("DROP TABLE [CUSTOMER]");
    }

    @Test
    void detectsExistingAndMissingIndexes() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of())
                .thenReturn(List.of(java.util.Map.of("name", "IDX")));

        assertEquals(true, sqlOps.existIndexQuery(jdbcTemplate, "CUSTOMER", "IDX"));
        assertEquals(false, sqlOps.existIndexQuery(jdbcTemplate, "CUSTOMER", "IDX"));
    }

    @Test
    void oracleCountUsesMetadataAndWrapsFailures() {
        OracleOps oracleOps = new OracleOps();
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Long.class),
                org.mockito.ArgumentMatchers.eq("FBNK_CUSTOMER"), org.mockito.ArgumentMatchers.eq("FBNK_CUSTOMER")))
                .thenReturn(42L);

        assertEquals(42L, oracleOps.countAll(jdbcTemplate, "FBNK_CUSTOMER"));

        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Long.class),
                org.mockito.ArgumentMatchers.eq("BROKEN"), org.mockito.ArgumentMatchers.eq("BROKEN")))
                .thenThrow(new RuntimeException("db unavailable"));
        assertThrows(IllegalStateException.class, () -> oracleOps.countAll(jdbcTemplate, "BROKEN"));
    }

    private SchemaSource schema(String column, String type) {
        return new SchemaSource("id-" + column, "CUSTOMER", column, type, true, false, "c1", "S", false);
    }
}
