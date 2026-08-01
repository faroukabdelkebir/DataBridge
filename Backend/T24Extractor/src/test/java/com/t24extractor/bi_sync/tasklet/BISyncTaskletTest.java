package com.t24extractor.bi_sync.tasklet;

import static com.t24extractor.commons.Constants.BI_STANDARD_SELECTION;
import static com.t24extractor.commons.Constants.NEW_TABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.t24extractor.model.SchemaSource;
import com.t24extractor.repository.bi_repos.MetaDataRepository;
import com.t24extractor.repository.bi_repos.ScheduledOpsRepository;
import com.t24extractor.repository.bi_repos.SchemaSourceAuditRepository;
import com.t24extractor.repository.bi_repos.SchemaSourceRepository;
import com.t24extractor.support.BatchTestContext;
import java.util.List;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class BISyncTaskletTest {

    private final SchemaSourceRepository schemaRepository = mock(SchemaSourceRepository.class);
    private final SchemaSourceAuditRepository auditRepository = mock(SchemaSourceAuditRepository.class);
    private final MetaDataRepository metadataRepository = mock(MetaDataRepository.class);
    private final ScheduledOpsRepository scheduledOpsRepository = mock(ScheduledOpsRepository.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    @Test
    void marksUnregisteredSchemaAsNewTable() throws Exception {
        BISyncTasklet tasklet = tasklet(new JSONObject("{\"NAME\":\"String.c1\"}"));
        when(schemaRepository.findSchemaSourceByTableNameAndEnabled("CUSTOMER", true))
                .thenReturn(List.of());
        when(schemaRepository.countByTableName("CUSTOMER")).thenReturn(0L);
        BatchTestContext context = BatchTestContext.create();

        RepeatStatus result = tasklet.execute(context.contribution(), context.chunkContext());

        assertEquals(RepeatStatus.FINISHED, result);
        assertEquals(NEW_TABLE, context.contribution().getExitStatus().getExitCode());
        assertEquals(NEW_TABLE, context.jobExecution().getExitStatus().getExitCode());
        verify(jdbcTemplate).batchUpdate(anyString());
    }

    @Test
    void preparesColumnsAndInsertSqlForKnownSchema() throws Exception {
        SchemaSource name = schema("NAME");
        BISyncTasklet tasklet = tasklet(new JSONObject("{\"NAME\":\"String.c1\",\"EXTRA\":\"String.c2\"}"));
        when(schemaRepository.findSchemaSourceByTableNameAndEnabled("CUSTOMER", true))
                .thenReturn(List.of(name));
        when(schemaRepository.countByTableName("CUSTOMER")).thenReturn(1L);
        when(auditRepository.findAllBySourceTableName("CUSTOMER")).thenReturn(List.of());
        when(schemaRepository.findAll("CUSTOMER")).thenReturn(List.of(name));
        BatchTestContext context = BatchTestContext.create();

        tasklet.execute(context.contribution(), context.chunkContext());

        assertEquals("{\"NAME\":\"String.c1\"}",
                context.jobExecution().getExecutionContext().getString(BI_STANDARD_SELECTION));
        assertEquals(List.of("_id", "ORIGINAL_ID", "NAME"),
                context.jobExecution().getExecutionContext().get("COLUMNS"));
        assertEquals("INSERT INTO [CUSTOMER]([_id],[ORIGINAL_ID],[NAME]) VALUES (?,?,?);",
                context.jobExecution().getExecutionContext().getString("SQL_QUERY"));
    }

    @Test
    void cleansStandardSelectionUsingEnabledSchema() {
        BISyncTasklet tasklet = tasklet(new JSONObject());
        when(schemaRepository.findAll("CUSTOMER")).thenReturn(List.of(schema("NAME"), schema("MISSING")));
        JSONObject source = new JSONObject("{\"NAME\":\"String.c1\",\"EXTRA\":\"String.c2\"}");

        JSONObject result = tasklet.ssMapClean("CUSTOMER", source);

        assertEquals("String.c1", result.getString("NAME"));
        assertFalse(result.has("EXTRA"));
        assertTrue(source.isEmpty());
    }

    private BISyncTasklet tasklet(JSONObject selection) {
        BISyncTasklet tasklet = new BISyncTasklet(
                schemaRepository, auditRepository, metadataRepository, scheduledOpsRepository, jdbcTemplate);
        tasklet.setT24TableName("CUSTOMER");
        tasklet.setSs(selection);
        return tasklet;
    }

    private SchemaSource schema(String column) {
        return new SchemaSource("id-" + column, "CUSTOMER", column, "String",
                true, false, "c1", "S", false);
    }
}
