package com.t24extractor.tasklet;

import static com.t24extractor.commons.Constants.BI_STANDARD_SELECTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.t24extractor.support.BatchTestContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class JsonFlattenerTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    @Test
    void createsAndPopulatesMultiValueTablesForExistingColumns() throws Exception {
        Json_Flattener tasklet = new Json_Flattener(jdbcTemplate);
        tasklet.setTable("CUSTOMER");
        BatchTestContext context = BatchTestContext.create();
        context.jobExecution().getExecutionContext()
                .putString(BI_STANDARD_SELECTION, "{\"PHONES\":\"StringMAX.c2\"}");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("CUSTOMER"), eq("PHONES")))
                .thenReturn(1);

        RepeatStatus result = tasklet.execute(context.contribution(), context.chunkContext());

        assertEquals(RepeatStatus.FINISHED, result);
        verify(jdbcTemplate).execute(org.mockito.ArgumentMatchers.contains("CUSTOMER#PHONES"));

        // Idempotence : le satellite est purgé (1er batch) avant d'être réalimenté (2e batch).
        ArgumentCaptor<String[]> queries = ArgumentCaptor.forClass(String[].class);
        verify(jdbcTemplate, times(2)).batchUpdate(queries.capture());

        assertEquals("IF EXISTS (SELECT * FROM sys.tables WHERE name='CUSTOMER#PHONES') "
                + "DELETE FROM [CUSTOMER#PHONES]", queries.getAllValues().get(0)[0]);
        assertEquals("""
                INSERT INTO [CUSTOMER#PHONES] (_id, PHONES, [MV_SEQUENCE], [SEQUENCE])
                SELECT _id, lda.PHONES, lda.[MV_SEQUENCE], lda.[SEQUENCE]
                FROM CUSTOMER AS ld
                CROSS APPLY OPENJSON(ld.PHONES)
                WITH (
                  PHONES nvarchar(255) N'strict $.PHONES',
                  [MV_SEQUENCE] int N'$.mvsequence',
                  [SEQUENCE] int N'$.sequence'
                ) AS lda""", queries.getAllValues().get(1)[0]);
    }

    @Test
    void purgesSatelliteBeforeEveryReloadSoRerunsDoNotDuplicateRows() throws Exception {
        Json_Flattener tasklet = new Json_Flattener(jdbcTemplate);
        tasklet.setTable("AA_BILL_DETAILS");
        BatchTestContext context = BatchTestContext.create();
        context.jobExecution().getExecutionContext()
                .putString(BI_STANDARD_SELECTION, "{\"SETTLE_STATUS\":\"StringMAX.c2\"}");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString(), anyString()))
                .thenReturn(1);

        tasklet.execute(context.contribution(), context.chunkContext());
        tasklet.execute(context.contribution(), context.chunkContext());

        // 2 exécutions x (purge + insert) : chaque run repart d'un satellite vide.
        ArgumentCaptor<String[]> queries = ArgumentCaptor.forClass(String[].class);
        verify(jdbcTemplate, times(4)).batchUpdate(queries.capture());
        assertTrue(queries.getAllValues().get(2)[0].startsWith(
                "IF EXISTS (SELECT * FROM sys.tables WHERE name='AA_BILL_DETAILS#SETTLE_STATUS')"));
        assertTrue(queries.getAllValues().get(3)[0].startsWith(
                "INSERT INTO [AA_BILL_DETAILS#SETTLE_STATUS]"));
    }

    @Test
    void multiValueLocalRefColumnGoesThroughTheGenericSatelliteShape() throws Exception {
        Json_Flattener tasklet = new Json_Flattener(jdbcTemplate);
        tasklet.setTable("CUSTOMER");
        BatchTestContext context = BatchTestContext.create();
        // Forme réelle produite par StandardSelectionTasklet pour un champ local
        // multivalué : la clé est le nom de colonne, le type est dans la valeur.
        context.jobExecution().getExecutionContext()
                .putString(BI_STANDARD_SELECTION, "{\"MV_LOCAL\":\"Local_Ref_StringMAX.m2\"}");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString(), anyString()))
                .thenReturn(1);

        tasklet.execute(context.contribution(), context.chunkContext());

        ArgumentCaptor<String> ddl = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(ddl.capture());
        assertTrue(ddl.getValue().contains("[CUSTOMER#MV_LOCAL]"));
        // ATTENTION — comportement actuel, pas nécessairement voulu : la colonne
        // MV_SEQUENCE est créée alors que le JSON des champs LOCAL.REF ne contient
        // que "sequence" (cf. XMLProcessor.putLocalRefCollection). Elle restera
        // donc NULL. La branche prévue pour ce cas dans Json_Flattener teste
        // key.contains("Local_Ref") au lieu de la valeur : elle ne s'exécute jamais.
        assertTrue(ddl.getValue().contains("[MV_SEQUENCE]"));
    }

    @Test
    void skipsDateTimeAndNonMultiValueColumns() throws Exception {
        Json_Flattener tasklet = new Json_Flattener(jdbcTemplate);
        tasklet.setTable("CUSTOMER");
        BatchTestContext context = BatchTestContext.create();
        context.jobExecution().getExecutionContext().putString(BI_STANDARD_SELECTION,
                "{\"DATE_TIME\":\"StringMAX.c1\",\"NAME\":\"String.c2\"}");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString(), anyString()))
                .thenReturn(1);

        tasklet.execute(context.contribution(), context.chunkContext());

        // DATE_TIME est exclu explicitement, NAME n'est pas multivalué (pas de "MAX").
        verify(jdbcTemplate, never()).execute(anyString());
        verify(jdbcTemplate, never()).batchUpdate(org.mockito.ArgumentMatchers.<String[]>any());
    }

    @Test
    void ignoresMetadataColumnsMissingFromDestinationTable() throws Exception {
        Json_Flattener tasklet = new Json_Flattener(jdbcTemplate);
        tasklet.setTable("CUSTOMER");
        BatchTestContext context = BatchTestContext.create();
        context.jobExecution().getExecutionContext()
                .putString(BI_STANDARD_SELECTION, "{\"PHONES\":\"StringMAX.c2\"}");
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString(), anyString()))
                .thenReturn(0);

        tasklet.execute(context.contribution(), context.chunkContext());

        verify(jdbcTemplate, never()).execute(anyString());
        verify(jdbcTemplate, never()).batchUpdate(org.mockito.ArgumentMatchers.<String[]>any());
    }
}
