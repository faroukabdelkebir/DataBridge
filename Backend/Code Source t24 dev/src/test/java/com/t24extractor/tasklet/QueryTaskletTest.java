package com.t24extractor.tasklet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.t24extractor.support.BatchTestContext;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class QueryTaskletTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    @Test
    void buildsFullQueryWithoutPartitionForSmallTable() throws Exception {
        QueryTasklet tasklet = configuredTasklet("Full");
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyString(), anyString())).thenReturn(10L);
        BatchTestContext context = xmlContext();

        tasklet.execute(context.contribution(), context.chunkContext());

        assertEquals("SELECT  RECID , XMLRECORD FROM FBNK_CUSTOMER ",
                context.jobExecution().getExecutionContext().getString("QUERY"));
    }

    @Test
    void buildsQuotedIncrementalRangeAndPartitionClause() throws Exception {
        QueryTasklet tasklet = configuredTasklet("Incremental");
        tasklet.setColumnRef("BOOKING_DATE");
        tasklet.setLastDate("20260101");
        tasklet.setFutureDate("20260131");
        tasklet.setDateFormat("varchar");
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyString(), anyString())).thenReturn(1000L);
        BatchTestContext context = xmlContext();

        tasklet.execute(context.contribution(), context.chunkContext());

        assertEquals(
                "SELECT  RECID , XMLRECORD FROM FBNK_CUSTOMER WHERE BOOKING_DATE > '20260101' AND BOOKING_DATE <= '20260131' AND MOD(ORA_HASH(ROWID), ?) = ?",
                context.jobExecution().getExecutionContext().getString("QUERY"));
    }

    @Test
    void doesNotExposeXmlQueryForBlobTable() throws Exception {
        QueryTasklet tasklet = configuredTasklet("Full");
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyString(), anyString())).thenReturn(0L);
        BatchTestContext context = BatchTestContext.create();
        context.jobExecution().getExecutionContext().put("Type", 'B');

        tasklet.execute(context.contribution(), context.chunkContext());

        assertEquals(false, context.jobExecution().getExecutionContext().containsKey("QUERY"));
    }

    @Test
    void incrementalWithoutReferenceColumnFallsBackToFullRead() throws Exception {
        QueryTasklet tasklet = configuredTasklet("Incremental");
        tasklet.setLastDate("20260101");
        smallTable();

        // Sans colonne de référence, aucune borne n'est calculable : on relit tout
        // plutôt que de produire une clause invalide.
        assertEquals("SELECT  RECID , XMLRECORD FROM FBNK_CUSTOMER ", queryOf(tasklet));
    }

    @Test
    void incrementalFromTheEpochSentinelReadsEverything() throws Exception {
        QueryTasklet tasklet = configuredTasklet("Incremental");
        tasklet.setColumnRef("BOOKING_DATE");
        tasklet.setLastDate("00000000");
        smallTable();

        // "00000000" est la valeur d'amorçage : première extraction, pas de borne.
        assertEquals("SELECT  RECID , XMLRECORD FROM FBNK_CUSTOMER ", queryOf(tasklet));
    }

    @Test
    void incrementalOnNumericColumnDoesNotQuoteTheDate() throws Exception {
        QueryTasklet tasklet = configuredTasklet("Incremental");
        tasklet.setColumnRef("BOOKING_DATE");
        tasklet.setLastDate("20260101");
        tasklet.setDateFormat("NUMBER");
        smallTable();

        assertEquals("SELECT  RECID , XMLRECORD FROM FBNK_CUSTOMER WHERE BOOKING_DATE > 20260101",
                queryOf(tasklet));
    }

    @Test
    void incrementalRangeOnNumericColumnIsUnquoted() throws Exception {
        QueryTasklet tasklet = configuredTasklet("Incremental");
        tasklet.setColumnRef("BOOKING_DATE");
        tasklet.setLastDate("20260101");
        tasklet.setFutureDate("20260131");
        tasklet.setDateFormat("NUMBER");
        smallTable();

        assertEquals(
                "SELECT  RECID , XMLRECORD FROM FBNK_CUSTOMER WHERE BOOKING_DATE > 20260101 AND BOOKING_DATE <= 20260131",
                queryOf(tasklet));
    }

    @Test
    void incrementalByIdIgnoresDateBounds() throws Exception {
        QueryTasklet tasklet = configuredTasklet("Incremental-id");
        tasklet.setColumnRef("BOOKING_DATE");
        tasklet.setLastDate("20260101");
        smallTable();

        assertEquals("SELECT  RECID , XMLRECORD FROM FBNK_CUSTOMER ", queryOf(tasklet));
    }

    @Test
    void incrementalSpecificSelectsCurrentAndPreviousMonthByRecid() throws Exception {
        QueryTasklet tasklet = configuredTasklet("Incremental-specific");
        tasklet.setLastDate("202601");
        smallTable();

        assertEquals("SELECT  RECID , XMLRECORD FROM FBNK_CUSTOMER  WHERE RECID like "
                        + "CONCAT('%-', to_char(sysdate, 'yyyymm')) OR RECID like "
                        + "CONCAT('%-', to_char(sysdate, 'yyyymm')-1)",
                queryOf(tasklet));
    }

    @Test
    void incrementalSpecificFromSentinelReadsEverything() throws Exception {
        QueryTasklet tasklet = configuredTasklet("Incremental-specific");
        tasklet.setLastDate("000000");
        smallTable();

        assertEquals("SELECT  RECID , XMLRECORD FROM FBNK_CUSTOMER ", queryOf(tasklet));
    }

    @Test
    void unknownLoadingModeReadsEverythingRatherThanFailing() throws Exception {
        QueryTasklet tasklet = configuredTasklet("Inconnu");
        smallTable();

        assertEquals("SELECT  RECID , XMLRECORD FROM FBNK_CUSTOMER ", queryOf(tasklet));
    }

    @Test
    void partitioningClauseIsChainedWithAndWhenAFilterExists() throws Exception {
        QueryTasklet tasklet = configuredTasklet("Incremental");
        tasklet.setColumnRef("BOOKING_DATE");
        tasklet.setLastDate("20260101");
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyString(), anyString())).thenReturn(5_000L);

        // Une clause WHERE existe déjà : le partitionnement doit s'y ajouter en AND,
        // sinon la requête est syntaxiquement invalide.
        assertEquals("SELECT  RECID , XMLRECORD FROM FBNK_CUSTOMER WHERE BOOKING_DATE > '20260101' "
                + "AND MOD(ORA_HASH(ROWID), ?) = ?", queryOf(tasklet));
    }

    private void smallTable() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyString(), anyString())).thenReturn(10L);
    }

    private String queryOf(QueryTasklet tasklet) throws Exception {
        BatchTestContext context = xmlContext();
        tasklet.execute(context.contribution(), context.chunkContext());
        return context.jobExecution().getExecutionContext().getString("QUERY");
    }

    private QueryTasklet configuredTasklet(String loadingType) {
        QueryTasklet tasklet = new QueryTasklet(jdbcTemplate);
        tasklet.setTableName("FBNK_CUSTOMER");
        tasklet.setChargement(loadingType);
        return tasklet;
    }

    private BatchTestContext xmlContext() {
        BatchTestContext context = BatchTestContext.create();
        context.jobExecution().getExecutionContext().put("Type", 'X');
        return context;
    }
}
