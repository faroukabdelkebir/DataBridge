package com.t24extractor.tasklet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.t24extractor.model.StandardSelectionRecord;
import com.t24extractor.model.TafjVoc;
import com.t24extractor.repository.t24_repo.StandardSelectionRepository;
import com.t24extractor.repository.t24_repo.TafjVocRepository;
import com.t24extractor.support.BatchTestContext;
import java.util.Optional;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class StandardSelectionTaskletTest {

    private final JdbcTemplate jdbcBi = mock(JdbcTemplate.class);
    private final TafjVocRepository tafjVocRepository = mock(TafjVocRepository.class);
    private final StandardSelectionRepository standardSelectionRepository =
            mock(StandardSelectionRepository.class);

    @AfterEach
    void clearSharedState() {
        StandardSelectionTasklet.getBlobViewFields().clear();
    }

    @Test
    void buildsStandardSelectionAndBlobViewQuery() throws Exception {
        StandardSelectionTasklet tasklet = configuredTasklet("CUSTOMER", "FBNK_CUSTOMER", "B");
        tasklet.setTableType("X");
        BatchTestContext context = BatchTestContext.create();

        RepeatStatus result = tasklet.execute(context.contribution(), context.chunkContext());

        assertEquals(RepeatStatus.FINISHED, result);
        JSONObject selection = new JSONObject(
                context.jobExecution().getExecutionContext().getString("Mongo_Standard_Selection"));
        assertEquals("NAME", selection.getJSONArray("sysField").getJSONObject(0).getString("c1"));
        assertEquals("M", selection.getJSONArray("typeField").getJSONObject(0).getString("c1"));
        assertTrue(context.jobExecution().getExecutionContext().getString("VIEW_QUERY")
                .contains("SELECT RECID as FIELD_ID, NAME, ROWNUM rowNumber FROM V_FBNK_CUSTOMER t"));
        assertEquals("B", context.jobExecution().getExecutionContext().getString("TYPE"));
        verify(jdbcBi).execute(contains("UPDATE batch_config"));
    }

    @Test
    void resolvesHistoryStandardSelectionFromBaseTable() throws Exception {
        StandardSelectionTasklet tasklet = configuredTasklet("ACCOUNT$HIS", "FBNK_ACCOUNT#HIS", "X");
        tasklet.setTableType("X");
        BatchTestContext context = BatchTestContext.create();

        tasklet.execute(context.contribution(), context.chunkContext());

        verify(standardSelectionRepository).findById("ACCOUNT");
        verify(jdbcBi, never()).execute(contains("UPDATE batch_config"));
    }

    @Test
    void defaultsSystemFieldToStringWhenDataTypeIsMissing() throws Exception {
        StandardSelectionTasklet tasklet = configuredTasklet(
                "FUNDS.TRANSFER$HIS",
                "FBNK_FUNDS_TRANSFER001",
                "X",
                """
                <row>
                  <c6 m="147">35</c6>
                  <c10 m="147">S</c10>
                  <c2 m="147">D</c2>
                  <c3 m="147">147</c3>
                  <c1 m="147">MISSING.TYPE.FIELD</c1>
                </row>
                """);
        tasklet.setTableType("X");
        BatchTestContext context = BatchTestContext.create();

        RepeatStatus result = tasklet.execute(context.contribution(), context.chunkContext());

        assertEquals(RepeatStatus.FINISHED, result);
        JSONObject sysFieldOds = new JSONObject(
                context.jobExecution().getExecutionContext().getString("sysFieldOds"));
        assertEquals("String35.c147", sysFieldOds.getString("MISSING_TYPE_FIELD"));
    }

    private StandardSelectionTasklet configuredTasklet(
            String t24Table, String oracleTable, String type) {
        return configuredTasklet(t24Table, oracleTable, type, """
                <row>
                  <c4 m="1">TEXT</c4>
                  <c6 m="1">35</c6>
                  <c10 m="1">S</c10>
                  <c2 m="1">D</c2>
                  <c3 m="1">1</c3>
                  <c1 m="1">NAME</c1>
                </row>
                """);
    }

    private StandardSelectionTasklet configuredTasklet(
            String t24Table, String oracleTable, String type, String xmlRecord) {
        when(tafjVocRepository.findTafjVocByOrclFileName(oracleTable))
                .thenReturn(Optional.of(new TafjVoc(t24Table, type, oracleTable)));
        StandardSelectionRecord record = new StandardSelectionRecord();
        record.setRecid(t24Table);
        record.setXmlRecord(xmlRecord);
        String lookup = t24Table.contains("$") ? t24Table.substring(0, t24Table.indexOf('$')) : t24Table;
        when(standardSelectionRepository.findById(lookup)).thenReturn(Optional.of(record));

        StandardSelectionTasklet tasklet =
                new StandardSelectionTasklet(jdbcBi, tafjVocRepository, standardSelectionRepository);
        tasklet.setOracleTableName(oracleTable);
        tasklet.setT24TableName(t24Table);
        tasklet.setTableType("B");
        return tasklet;
    }
}
