package com.t24extractor.bi_sync.tasklet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.t24extractor.model.MetaDataTable;
import com.t24extractor.model.ScheduledOps;
import com.t24extractor.model.SchemaSource;
import com.t24extractor.model.SchemaSourceAudit;
import com.t24extractor.repository.bi_repos.MetaDataRepository;
import com.t24extractor.repository.bi_repos.ScheduledOpsRepository;
import com.t24extractor.repository.bi_repos.SchemaSourceAuditRepository;
import com.t24extractor.repository.bi_repos.SchemaSourceRepository;
import com.t24extractor.support.BatchTestContext;
import java.util.List;
import java.util.Optional;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Évolution de schéma pilotée par SCHEMA_SOURCE_AUDIT.
 *
 * Quand un utilisateur active ou désactive une colonne dans l'IHM, une ligne
 * d'audit est produite ; BI_SYNC doit la traduire en ALTER TABLE, en index et
 * en métadonnées multivaluées, puis purger l'audit. Ces chemins n'étaient pas
 * couverts : une erreur y corrompt durablement la table BI.
 */
class BISyncTaskletSchemaChangeTest {

    private final SchemaSourceRepository schemaRepository = mock(SchemaSourceRepository.class);
    private final SchemaSourceAuditRepository auditRepository = mock(SchemaSourceAuditRepository.class);
    private final MetaDataRepository metadataRepository = mock(MetaDataRepository.class);
    private final ScheduledOpsRepository scheduledOpsRepository = mock(ScheduledOpsRepository.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    @Test
    void activatingAMultiValueColumnAddsItAndRegistersItsMetadata() throws Exception {
        givenTableIsKnownWith("NAME");
        givenAudit(audit("PHONES", "StringMAX", 1, 0, 0, 0));
        when(schemaRepository.findAllByTableNameAndColumnName("CUSTOMER", "PHONES"))
                .thenReturn(Optional.of(schema("PHONES", "StringMAX")));

        execute();

        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).batchUpdate(statements.capture());
        assertTrue(statements.getAllValues().stream().anyMatch(sql -> sql.contains("ADD [PHONES]")),
                "la colonne activée doit être ajoutée à la table BI");
        assertTrue(statements.getAllValues().stream()
                        .anyMatch(sql -> sql.contains("INSERT INTO T24_Metadata")
                                && sql.contains("'PHONES'")),
                "un champ multivalué doit être déclaré dans T24_Metadata");
        verify(auditRepository).deleteAll(org.mockito.ArgumentMatchers.anyIterable());
    }

    @Test
    void deactivatingAMultiValueColumnDropsItAndItsMetadata() throws Exception {
        givenTableIsKnownWith("NAME");
        givenAudit(audit("PHONES", "StringMAX", 0, 1, 0, 0));
        when(schemaRepository.findAllByTableNameAndColumnName("CUSTOMER", "PHONES"))
                .thenReturn(Optional.of(schema("PHONES", "StringMAX")));

        execute();

        verify(metadataRepository).delete(new MetaDataTable("CUSTOMER", "PHONES", false));
        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).batchUpdate(statements.capture());
        assertTrue(statements.getAllValues().stream().anyMatch(sql -> sql.contains("DROP COLUMN PHONES")));
    }

    @Test
    void failsWhenAuditReferencesAnUnknownColumn() {
        givenTableIsKnownWith("NAME");
        givenAudit(audit("GHOST", "StringMAX", 1, 0, 0, 0));
        when(schemaRepository.findAllByTableNameAndColumnName("CUSTOMER", "GHOST"))
                .thenReturn(Optional.empty());

        // Mieux vaut échouer que d'altérer la table sur une colonne fantôme.
        assertThrows(RuntimeException.class, this::execute);
    }

    @Test
    void indexingASmallTableCreatesTheIndexImmediately() throws Exception {
        givenTableIsKnownWith("NAME");
        givenAudit(audit("DATE_TIME", "String", 0, 0, 1, 0));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(1_000L);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        execute();

        verify(jdbcTemplate).execute("CREATE NONCLUSTERED INDEX [DATE_TIME] ON [CUSTOMER]([DATE_TIME])");
        verify(scheduledOpsRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void indexingAHugeTableIsDeferredToScheduledOps() throws Exception {
        givenTableIsKnownWith("NAME");
        givenAudit(audit("DATE_TIME", "String", 0, 0, 1, 0));
        // Au-delà de 10 M de lignes, créer l'index en ligne bloquerait la table.
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(10_000_001L);

        execute();

        verify(scheduledOpsRepository).save(new ScheduledOps(null, "[CUSTOMER]", "[DATE_TIME]", "I"));
        verify(jdbcTemplate, never()).execute(contains("CREATE NONCLUSTERED INDEX"));
    }

    @Test
    void removingAnIndexDropsIt() throws Exception {
        givenTableIsKnownWith("NAME");
        givenAudit(audit("DATE_TIME", "String", 0, 0, 0, 1));

        execute();

        verify(jdbcTemplate).execute("DROP INDEX [DATE_TIME]");
    }

    @Test
    void missingDestinationColumnIsRecreatedBeforeInsert() throws Exception {
        givenTableIsKnownWith("NAME", "PHONES");
        when(auditRepository.findAllBySourceTableName("CUSTOMER")).thenReturn(List.of());
        // La table cible a perdu PHONES (run interrompu, audit purgé…).
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of("NAME"));

        execute();

        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).batchUpdate(statements.capture());
        assertTrue(statements.getAllValues().stream().anyMatch(sql -> sql.contains("ADD [PHONES]")),
                "l'auto-réparation doit matérialiser la colonne manquante");
    }

    @Test
    void registersMultiValueAndLocalRefMetadataForTheWholeSchema() {
        BISyncTasklet tasklet = tasklet();

        tasklet.loadMetaDataTable("CUSTOMER", List.of(
                schema("PHONES", "StringMAX"), localRef("LOCAL_REF_1")));

        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).batchUpdate(statements.capture());
        assertTrue(statements.getAllValues().get(0).contains("'CUSTOMER','PHONES','false'"));
        assertTrue(statements.getAllValues().get(1).contains("'CUSTOMER','LOCAL_REF_1','true'"));
    }

    @Test
    void countTableDelegatesToSqlServerStatistics() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(42L);

        assertEquals(42L, tasklet().countTable(jdbcTemplate, "CUSTOMER"));
    }

    @Test
    void unusedContractMethodsRemainSideEffectFree() {
        BISyncTasklet tasklet = tasklet();

        tasklet.scheduleOp("CUSTOMER");
        tasklet.loadSchemaSource("CUSTOMER", new JSONObject());
        tasklet.checkSchemaUpdate("CUSTOMER");

        verify(auditRepository).findAllBySourceTableName("CUSTOMER");
        verify(jdbcTemplate, never()).execute(anyString());
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private void execute() throws Exception {
        BatchTestContext context = BatchTestContext.create();
        tasklet().execute(context.contribution(), context.chunkContext());
    }

    private BISyncTasklet tasklet() {
        BISyncTasklet tasklet = new BISyncTasklet(
                schemaRepository, auditRepository, metadataRepository, scheduledOpsRepository, jdbcTemplate);
        tasklet.setT24TableName("CUSTOMER");
        tasklet.setSs(new JSONObject("{\"NAME\":\"String.c1\",\"PHONES\":\"StringMAX.c2\"}"));
        return tasklet;
    }

    private void givenTableIsKnownWith(String... columns) {
        List<SchemaSource> schema = java.util.Arrays.stream(columns)
                .map(column -> schema(column, "PHONES".equals(column) ? "StringMAX" : "String"))
                .toList();
        when(schemaRepository.countByTableName("CUSTOMER")).thenReturn((long) schema.size());
        when(schemaRepository.findSchemaSourceByTableNameAndEnabled("CUSTOMER", true)).thenReturn(schema);
        when(schemaRepository.findAll("CUSTOMER")).thenReturn(schema);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(java.util.Arrays.asList(columns));
    }

    private void givenAudit(SchemaSourceAudit... audits) {
        when(auditRepository.findAllBySourceTableName("CUSTOMER")).thenReturn(List.of(audits));
    }

    private SchemaSourceAudit audit(String column, String dataType,
            int newEnabled, int oldEnabled, int newIndexed, int oldIndexed) {
        return new SchemaSourceAudit(1, "id-" + column, "CUSTOMER", column, dataType,
                newEnabled, oldEnabled, newIndexed, oldIndexed);
    }

    private SchemaSource schema(String column, String dataType) {
        return new SchemaSource("id-" + column, "CUSTOMER", column, dataType,
                true, false, "c1", "S", false);
    }

    private SchemaSource localRef(String column) {
        return new SchemaSource("id-" + column, "CUSTOMER", column, "StringMAX",
                true, false, "c1", "S", true);
    }
}
