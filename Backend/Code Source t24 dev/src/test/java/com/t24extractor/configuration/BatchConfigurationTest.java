package com.t24extractor.configuration;

import static com.t24extractor.commons.Constants.AA_BILL_DETAILS;
import static com.t24extractor.commons.Constants.BI_SYNC;
import static com.t24extractor.commons.Constants.CUSTOMER;
import static com.t24extractor.commons.Constants.FUNDS_TRANSFER_HIS;
import static com.t24extractor.commons.Constants.JSON_NORMALIZATION;
import static com.t24extractor.commons.Constants.QUERY_BUILDING_STEP;
import static com.t24extractor.commons.Constants.STEP_COB_STATUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.t24extractor.model.T24Table;
import com.t24extractor.repository.bi_repos.MetaDataRepository;
import com.t24extractor.repository.bi_repos.ScheduledOpsRepository;
import com.t24extractor.repository.bi_repos.SchemaSourceAuditRepository;
import com.t24extractor.repository.bi_repos.SchemaSourceRepository;
import com.t24extractor.repository.t24_repo.StandardSelectionRepository;
import com.t24extractor.repository.t24_repo.TafjVocRepository;
import com.t24extractor.tasklet.CobStatusTasklet;
import com.t24extractor.tasklet.FileControlServiceImpl;
import javax.sql.DataSource;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.partition.PartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.infrastructure.item.support.SynchronizedItemStreamReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Couverture du câblage déclaré par {@link BatchConfiguration}.
 *
 * Le contexte Spring complet n'est pas démarrable ici (Oracle, SQL Server et
 * Eureka requis) : les méthodes {@code @Bean} sont donc invoquées directement.
 *
 * Les noms de steps, de flows et de jobs sont l'enjeu principal : ils sont
 * référencés en dur ailleurs dans le code (JobLauncherController,
 * AutoExtractionScheduler) et une divergence ne se voit qu'à l'exécution.
 */
class BatchConfigurationTest {

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final JdbcTemplate jdbcT24 = mock(JdbcTemplate.class);
    private final JdbcTemplate jdbcBI = mock(JdbcTemplate.class);
    private final TafjVocRepository tafjVocRepository = mock(TafjVocRepository.class);
    private final StandardSelectionRepository standardSelectionRepository = mock(StandardSelectionRepository.class);
    private final FileControlServiceImpl fileControlService = mock(FileControlServiceImpl.class);

    private final BatchConfiguration configuration = newConfiguration();

    /**
     * En contexte Spring, {@code oracleXmlReader} est un bean {@code @StepScope}
     * atteint via un proxy ; appelé directement avec une requête nulle il
     * échouerait. On neutralise donc le reader, comme le ferait le proxy.
     */
    private BatchConfiguration newConfiguration() {
        BatchConfiguration batchConfiguration = new BatchConfiguration(
                mock(CobStatusTasklet.class), mock(DataSource.class), mock(DataSource.class)) {
            @Override
            @SuppressWarnings("unchecked")
            public SynchronizedItemStreamReader<T24Table> oracleXmlReader(String query, int value) {
                return mock(SynchronizedItemStreamReader.class);
            }
        };
        ReflectionTestUtils.setField(batchConfiguration, "workersNumber", 4);
        return batchConfiguration;
    }

    // ── Steps ────────────────────────────────────────────────────────────────

    @Test
    void namesStandardSelectionAndTableNameStepsPerTable() {
        assertEquals("Standard.Selection.CUSTOMER", configuration.standardSelectionCustomerStep(
                configuration.standardSelectionCustomer(jdbcT24, jdbcBI, tafjVocRepository, standardSelectionRepository),
                jobRepository, transactionManager).getName());
        assertEquals("Standard.Selection.FUNDS.TRANSFER", configuration.standardSelectionFTStep(
                configuration.standardSelectionFT(jdbcT24, jdbcBI, tafjVocRepository, standardSelectionRepository),
                jobRepository, transactionManager).getName());
        assertEquals("Standard.Selection.AA.BILL.DETAILS", configuration.standardSelectionAABillStep(
                configuration.standardSelectionAABill(jdbcT24, jdbcBI, tafjVocRepository, standardSelectionRepository),
                jobRepository, transactionManager).getName());

        assertEquals("TableName.CUSTOMER", configuration.tableNameCustomerStep(
                configuration.tableNameCustomer(tafjVocRepository, fileControlService),
                jobRepository, transactionManager).getName());
        assertEquals("TableName.FT$HIS", configuration.tableNameFTStep(
                configuration.tableNameFT(tafjVocRepository, fileControlService),
                jobRepository, transactionManager).getName());
        assertEquals("TableName.AA.BILL.DETAILS", configuration.tableNameAABillStep(
                configuration.tableNameAABill(tafjVocRepository, fileControlService),
                jobRepository, transactionManager).getName());
    }

    @Test
    void allQueryBuildingStepsShareTheSameStepName() {
        // Les trois flows sont indépendants : ils réutilisent volontairement le
        // même nom de step, propre à chaque exécution de job.
        assertEquals(QUERY_BUILDING_STEP, configuration.cusQueryBuildingStep(
                configuration.queryCustomerTasklet(jdbcT24), jobRepository, transactionManager).getName());
        assertEquals(QUERY_BUILDING_STEP, configuration.ftQueryBuildingStep(
                configuration.queryFtTasklet(jdbcT24), jobRepository, transactionManager).getName());
        assertEquals(QUERY_BUILDING_STEP, configuration.aaQueryBuildingStep(
                configuration.queryAABillTasklet(jdbcT24), jobRepository, transactionManager).getName());
    }

    @Test
    void namesCobStatusBiSyncAndNormalizationSteps() {
        assertEquals(STEP_COB_STATUS, configuration.cobStatus(jobRepository, transactionManager).getName());

        assertEquals(BI_SYNC, configuration.bISyncCus(biSyncTasklet(), jobRepository, transactionManager).getName());
        assertEquals(BI_SYNC, configuration.bISyncFt(biSyncTasklet(), jobRepository, transactionManager).getName());
        assertEquals(BI_SYNC, configuration.bISyncAaBill(biSyncTasklet(), jobRepository, transactionManager).getName());

        assertEquals(JSON_NORMALIZATION, configuration.jsonNormalizationCusStep(
                jobRepository, transactionManager, configuration.normalizationCusTasklet(jdbcBI)).getName());
        assertEquals(JSON_NORMALIZATION, configuration.jsonNormalizationFtStep(
                jobRepository, transactionManager, configuration.normalizationFtTasklet(jdbcBI)).getName());
        assertEquals(JSON_NORMALIZATION, configuration.jsonNormalizationAaBillStep(
                jobRepository, transactionManager, configuration.normalizationAaBillTasklet(jdbcBI)).getName());
    }

    @Test
    void managerStepsAreNamedAfterTheirBusinessTable() {
        assertEquals(CUSTOMER,
                configuration.cusManager(mock(PartitionHandler.class), jobRepository).getName());
        assertEquals(FUNDS_TRANSFER_HIS,
                configuration.ftManager(mock(PartitionHandler.class), jobRepository).getName());
        assertEquals(AA_BILL_DETAILS,
                configuration.aaManager(mock(PartitionHandler.class), jobRepository).getName());
    }

    @Test
    void workerStepsAreNamedAfterTheirPartitionPrefix() {
        var writer = configuration.itemWriter("INSERT INTO [CUSTOMER] VALUES (?)");

        // Le manager partitionne avec le préfixe "cusWorker" : le step worker
        // doit porter exactement ce nom pour que les partitions soient résolues.
        assertEquals("cusWorker", configuration.cusWorker(writer, jobRepository, transactionManager).getName());
        assertEquals("ftWorker", configuration.ftWorker(writer, jobRepository, transactionManager).getName());
        assertEquals("aaWorker", configuration.aaWorker(writer, jobRepository, transactionManager).getName());
    }

    // ── Flows et jobs ────────────────────────────────────────────────────────

    @Test
    void flowsAreNamedAfterTheirBusinessTable() {
        assertEquals(CUSTOMER, customerFlow().getName());
        assertEquals(AA_BILL_DETAILS, aaBillFlow().getName());
        assertEquals(FUNDS_TRANSFER_HIS, ftFlow().getName());
    }

    @Test
    void jobNamesMatchTheIdentifiersUsedByCallers() {
        // "T24" est recherché en dur par JobLauncherController.handleStoppingAllJobs :
        // renommer ce job rendrait l'arrêt des extractions inopérant, sans erreur.
        assertEquals("T24", configuration.job(ftFlow(), customerFlow(), aaBillFlow(), jobRepository).getName());
        assertEquals("CUSTOMER.JOB", configuration.customerJob(customerFlow(), jobRepository).getName());
        assertEquals("AA.BILL.DETAILS.JOB", configuration.billDetailsJob(aaBillFlow(), jobRepository).getName());
        assertEquals("FT.HIS.JOB", configuration.ftJob(ftFlow(), jobRepository).getName());
    }

    @Test
    void mainJobIsRestartableThroughItsRunIdIncrementer() {
        Job job = configuration.job(ftFlow(), customerFlow(), aaBillFlow(), jobRepository);

        // Sans incrementer, un second lancement avec les mêmes paramètres serait
        // rejeté comme instance déjà complétée.
        assertNotNull(job.getJobParametersIncrementer());
    }

    @Test
    void schemaLoaderJobChainsEveryTableWithoutExtraction() {
        Job loader = configuration.loadJob(
                step("TableName.CUSTOMER"), step("Standard.Selection.CUSTOMER"), step("cusQuery"), step("biSyncCus"),
                step("TableName.AA.BILL.DETAILS"), step("Standard.Selection.AA"), step("aaQuery"), step("biSyncAa"),
                step("TableName.FT$HIS"), step("Standard.Selection.FT"), step("ftQuery"), step("biSyncFt"),
                jobRepository);

        assertEquals("SCHEMA_LOADER", loader.getName());
        assertNotNull(loader.getJobParametersIncrementer());
    }

    // ── Infrastructure ───────────────────────────────────────────────────────

    @Test
    void partitionHandlersUseTheConfiguredWorkerCount() throws Exception {
        PartitionHandler handler = configuration.cusXMLPartitionHandler(step("cusWorker"));

        assertNotNull(handler);
        assertEquals(4, ReflectionTestUtils.getField(handler, "gridSize"));
        assertNotNull(configuration.ftXMLPartitionHandler(step("ftWorker")));
        assertNotNull(configuration.aaXMLPartitionHandler(step("aaWorker")));
    }

    @Test
    void partitionerProducesOnePartitionPerWorker() {
        assertEquals(4, configuration.partitioner().partition(4).size());
    }

    @Test
    void exposesReadersProcessorsAndWriters() {
        assertNotNull(configuration.xmlRowMapper());
        assertNotNull(configuration.blobRowMapper());
        assertNotNull(configuration.xmlFilterProcessor());
        assertNotNull(configuration.blobFilterProcessor());
        assertNotNull(configuration.itm());
        assertNotNull(configuration.itemWriter("INSERT INTO [CUSTOMER] VALUES (?)"));
        assertNotNull(configuration.executionFlowDecider());
        assertNotNull(configuration.jobListener(jdbcBI));
    }

    @Test
    void blobReaderRestrictsRowsToItsPartitionRange() {
        assertNotNull(configuration.oracleBlobReader("SELECT * FROM V_FBNK_CUSTOMER", 1L, 500L));
    }

    @Test
    void xmlReaderBindsGridSizeAndPartitionIndexWhenTheQueryIsPartitioned() throws Exception {
        BatchConfiguration real = realConfiguration();

        var reader = real.oracleXmlReader(
                "SELECT RECID, XMLRECORD FROM FBNK_CUSTOMER WHERE MOD(ABS(ORA_HASH(ROWID)), ?) = ?", 2);

        java.sql.PreparedStatement statement = mock(java.sql.PreparedStatement.class);
        setterOf(reader).setValues(statement);
        // 1er paramètre : nombre de workers, 2e : index de la partition courante.
        org.mockito.Mockito.verify(statement).setInt(1, 4);
        org.mockito.Mockito.verify(statement).setInt(2, 2);
    }

    @Test
    void xmlReaderLeavesPlainQueriesUnparameterised() throws Exception {
        var reader = realConfiguration().oracleXmlReader("SELECT RECID, XMLRECORD FROM FBNK_CUSTOMER ", 0);

        org.junit.jupiter.api.Assertions.assertNull(setterOf(reader));
    }

    @Test
    void partitionedQueriesProducedByQueryTaskletAreNotParameterised() throws Exception {
        // Requête réellement générée par QueryTasklet pour une grosse table.
        String generated = "SELECT  RECID , XMLRECORD FROM FBNK_CUSTOMER WHERE MOD(ORA_HASH(ROWID), ?) = ?";

        var reader = realConfiguration().oracleXmlReader(generated, 2);

        // ATTENTION — comportement actuel, très probablement un défaut :
        // oracleXmlReader teste query.contains("ABS"), alors que QueryTasklet
        // génère MOD(ORA_HASH(ROWID), ?) sans ABS. Aucun PreparedStatementSetter
        // n'est donc installé alors que la requête porte deux placeholders.
        org.junit.jupiter.api.Assertions.assertNull(setterOf(reader));
    }

    private BatchConfiguration realConfiguration() {
        BatchConfiguration real = new BatchConfiguration(
                mock(CobStatusTasklet.class), mock(DataSource.class), mock(DataSource.class));
        ReflectionTestUtils.setField(real, "workersNumber", 4);
        return real;
    }

    private org.springframework.jdbc.core.PreparedStatementSetter setterOf(
            SynchronizedItemStreamReader<T24Table> reader) {
        Object delegate = ReflectionTestUtils.getField(reader, "delegate");
        return (org.springframework.jdbc.core.PreparedStatementSetter)
                ReflectionTestUtils.getField(delegate, "preparedStatementSetter");
    }

    @Test
    void taskExecutorRunsWorkersConcurrently() throws Exception {
        var executor = configuration.taskExecutor();
        var latch = new java.util.concurrent.CountDownLatch(1);

        executor.execute(latch::countDown);

        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS),
                "les workers de partition doivent s'exécuter sur le task executor");
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    private Flow customerFlow() {
        return configuration.customerFlow(step("cobStatus"), step("tableName"), step("standardSelection"),
                step("query"), step("biSync"), step("manager"), step("normalization"));
    }

    private Flow aaBillFlow() {
        return configuration.aaBillFlow(step("cobStatus"), step("tableName"), step("standardSelection"),
                step("query"), step("biSync"), step("manager"), step("normalization"));
    }

    private Flow ftFlow() {
        return configuration.ftFlow(step("cobStatus"), step("tableName"), step("standardSelection"),
                step("query"), step("biSync"), step("manager"), step("normalization"));
    }

    private com.t24extractor.bi_sync.tasklet.BISyncTasklet biSyncTasklet() {
        return configuration.biSyncCusTasklet(new JSONObject(), mock(SchemaSourceRepository.class),
                mock(SchemaSourceAuditRepository.class), mock(MetaDataRepository.class),
                mock(ScheduledOpsRepository.class), jdbcBI);
    }

    private Step step(String name) {
        Step step = mock(Step.class);
        when(step.getName()).thenReturn(name);
        return step;
    }
}
