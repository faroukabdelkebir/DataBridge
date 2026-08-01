package com.t24extractor.configuration;

import static com.t24extractor.commons.Constants.BI_STANDARD_SELECTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.t24extractor.bi_sync.step.BISyncStep;
import com.t24extractor.bi_sync.tasklet.BISyncTasklet;
import com.t24extractor.model.SchemaSource;
import com.t24extractor.repository.bi_repos.MetaDataRepository;
import com.t24extractor.repository.bi_repos.ScheduledOpsRepository;
import com.t24extractor.repository.bi_repos.SchemaSourceAuditRepository;
import com.t24extractor.repository.bi_repos.SchemaSourceRepository;
import com.t24extractor.support.BatchTestContext;
import com.t24extractor.tasklet.CobStatusTasklet;
import com.t24extractor.tasklet.Json_Flattener;
import com.t24extractor.tasklet.QueryTasklet;
import java.util.List;
import java.util.function.Function;
import javax.sql.DataSource;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Tests de non-régression du câblage des beans batch.
 *
 * CUSTOMER, FUNDS.TRANSFER$HIS et AA.BILL.DETAILS sont configurés par trois
 * blocs de beans quasi identiques : une erreur de copier-coller (mauvaise table
 * Oracle, mauvaise colonne de référence, mauvais mode de chargement) ne casse
 * aucune compilation et ne se voit qu'en production, sur les données extraites.
 *
 * Ces tests vérifient le câblage par le COMPORTEMENT des beans produits — donc
 * sans démarrer le contexte Spring, impossible ici faute d'Oracle et SQL Server.
 */
class BatchWiringRegressionTest {

    private final JdbcTemplate jdbcT24 = mock(JdbcTemplate.class);
    private final JdbcTemplate jdbcBI = mock(JdbcTemplate.class);
    private final SchemaSourceRepository schemaSourceRepository = mock(SchemaSourceRepository.class);
    private final SchemaSourceAuditRepository schemaSourceAuditRepository = mock(SchemaSourceAuditRepository.class);
    private final MetaDataRepository metaDataRepository = mock(MetaDataRepository.class);
    private final ScheduledOpsRepository scheduledOpsRepository = mock(ScheduledOpsRepository.class);

    private final BatchConfiguration configuration = new BatchConfiguration(
            mock(CobStatusTasklet.class), mock(DataSource.class), mock(DataSource.class));

    // ── Requêtes de lecture Oracle ───────────────────────────────────────────

    @Test
    void eachTableReadsFromItsOwnOracleTable() throws Exception {
        // Petite table : pas de clause de partitionnement, la requête reste nue.
        when(jdbcT24.queryForObject(anyString(), eq(Long.class), anyString(), anyString())).thenReturn(10L);

        assertEquals("SELECT  RECID , XMLRECORD FROM FBNK_CUSTOMER ",
                queryProducedBy(configuration.queryCustomerTasklet(jdbcT24)));
        assertEquals("SELECT  RECID , XMLRECORD FROM FBNK_FUNDS_TRANSFER001 ",
                queryProducedBy(configuration.queryFtTasklet(jdbcT24)));
        assertEquals("SELECT  RECID , XMLRECORD FROM FBNK_AA_BILL_DETAILS ",
                queryProducedBy(configuration.queryAABillTasklet(jdbcT24)));
    }

    @Test
    void largeTablesAreReadThroughThePartitioningClause() throws Exception {
        // Au-delà de 1 000 lignes, la lecture doit être partitionnée : sans cette
        // clause, les workers liraient tous l'intégralité de la table.
        when(jdbcT24.queryForObject(anyString(), eq(Long.class))).thenReturn(50_000L);

        assertTrue(queryProducedBy(configuration.queryCustomerTasklet(jdbcT24))
                .endsWith("WHERE MOD(ORA_HASH(ROWID), ?) = ?"));
    }

    // ── Tables BI de destination ─────────────────────────────────────────────

    @Test
    void eachTableSyncsIntoItsOwnBiTable() throws Exception {
        assertEquals("INSERT INTO [CUSTOMER]([_id],[ORIGINAL_ID],[NAME]) VALUES (?,?,?);",
                insertProducedBy(configuration.biSyncCusTasklet(selection(), schemaSourceRepository,
                        schemaSourceAuditRepository, metaDataRepository, scheduledOpsRepository, jdbcBI),
                        "CUSTOMER"));

        // FUNDS.TRANSFER$HIS doit être renommée FUNDS_TRANSFER#HIS côté SQL Server.
        assertEquals("INSERT INTO [FUNDS_TRANSFER#HIS]([_id],[ORIGINAL_ID],[NAME]) VALUES (?,?,?);",
                insertProducedBy(configuration.biSyncFtTasklet(selection(), schemaSourceRepository,
                        schemaSourceAuditRepository, metaDataRepository, scheduledOpsRepository, jdbcBI),
                        "FUNDS_TRANSFER#HIS"));

        assertEquals("INSERT INTO [AA_BILL_DETAILS]([_id],[ORIGINAL_ID],[NAME]) VALUES (?,?,?);",
                insertProducedBy(configuration.biSyncAaBillTasklet(selection(), schemaSourceRepository,
                        schemaSourceAuditRepository, metaDataRepository, scheduledOpsRepository, jdbcBI),
                        "AA_BILL_DETAILS"));
    }

    @Test
    void normalizationTargetsTheSameBiTablesAsTheSyncStep() throws Exception {
        // Les satellites multivalués sont créés à partir du nom utilisé par
        // Json_Flattener : il doit coïncider avec la table alimentée par BI_SYNC.
        assertEquals("CUSTOMER#PHONES", satelliteCreatedBy(configuration::normalizationCusTasklet));
        assertEquals("FUNDS_TRANSFER#HIS#PHONES", satelliteCreatedBy(configuration::normalizationFtTasklet));
        assertEquals("AA_BILL_DETAILS#PHONES", satelliteCreatedBy(configuration::normalizationAaBillTasklet));
    }

    // ── Step BI_SYNC autonome ────────────────────────────────────────────────

    @Test
    void biSyncStepBuildsATaskletBoundToItsStepName() {
        BISyncStep stepConfiguration = new BISyncStep();
        BISyncTasklet tasklet = stepConfiguration.biSyncTasklet(selection(), schemaSourceRepository,
                schemaSourceAuditRepository, metaDataRepository, scheduledOpsRepository, jdbcBI);

        Step step = stepConfiguration.bISync(tasklet, mock(JobRepository.class),
                mock(PlatformTransactionManager.class));

        assertEquals("BI_SYNC", step.getName());
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    private String queryProducedBy(QueryTasklet tasklet) throws Exception {
        BatchTestContext context = BatchTestContext.create();
        context.jobExecution().getExecutionContext().put("Type", 'X');
        tasklet.execute(context.contribution(), context.chunkContext());
        return context.jobExecution().getExecutionContext().getString("QUERY");
    }

    private String insertProducedBy(BISyncTasklet tasklet, String biTable) throws Exception {
        SchemaSource name = new SchemaSource("id-NAME", biTable, "NAME", "String",
                true, false, "c1", "S", false);
        when(schemaSourceRepository.countByTableName(biTable)).thenReturn(1L);
        when(schemaSourceRepository.findSchemaSourceByTableNameAndEnabled(biTable, true)).thenReturn(List.of(name));
        when(schemaSourceRepository.findAll(biTable)).thenReturn(List.of(name));
        when(schemaSourceAuditRepository.findAllBySourceTableName(biTable)).thenReturn(List.of());
        when(jdbcBI.queryForList(anyString(), eq(String.class))).thenReturn(List.of("NAME"));

        BatchTestContext context = BatchTestContext.create();
        tasklet.execute(context.contribution(), context.chunkContext());
        return context.jobExecution().getExecutionContext().getString("SQL_QUERY");
    }

    /** Chaque bean reçoit son propre JdbcTemplate observable. */
    private String satelliteCreatedBy(Function<JdbcTemplate, Json_Flattener> beanFactory) throws Exception {
        JdbcTemplate template = mock(JdbcTemplate.class);
        when(template.queryForObject(anyString(), eq(Integer.class), anyString(), anyString())).thenReturn(1);

        BatchTestContext context = BatchTestContext.create();
        context.jobExecution().getExecutionContext()
                .putString(BI_STANDARD_SELECTION, "{\"PHONES\":\"StringMAX.c2\"}");
        beanFactory.apply(template).execute(context.contribution(), context.chunkContext());

        ArgumentCaptor<String> ddl = ArgumentCaptor.forClass(String.class);
        verify(template).execute(ddl.capture());
        return ddl.getValue().substring(ddl.getValue().indexOf("name='") + "name='".length(),
                ddl.getValue().indexOf("')"));
    }

    private JSONObject selection() {
        return new JSONObject("{\"NAME\":\"String.c1\"}");
    }
}
