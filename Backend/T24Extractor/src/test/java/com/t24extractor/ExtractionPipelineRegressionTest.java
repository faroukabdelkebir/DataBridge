package com.t24extractor;

import static com.t24extractor.commons.Constants.BI_STANDARD_SELECTION;
import static com.t24extractor.commons.Constants.CUSTOMER;
import static com.t24extractor.commons.Constants.FBNK_CUSTOMER;
import static com.t24extractor.commons.Constants.NEW_TABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.t24extractor.bi_sync.tasklet.BISyncTasklet;
import com.t24extractor.model.SchemaSource;
import com.t24extractor.model.StandardSelectionRecord;
import com.t24extractor.model.TafjVoc;
import com.t24extractor.partitioner.MssqlPartitioner;
import com.t24extractor.repository.bi_repos.MetaDataRepository;
import com.t24extractor.repository.bi_repos.ScheduledOpsRepository;
import com.t24extractor.repository.bi_repos.SchemaSourceAuditRepository;
import com.t24extractor.repository.bi_repos.SchemaSourceRepository;
import com.t24extractor.repository.t24_repo.StandardSelectionRepository;
import com.t24extractor.repository.t24_repo.TafjVocRepository;
import com.t24extractor.support.BatchTestContext;
import com.t24extractor.tasklet.FileControlServiceImpl;
import com.t24extractor.tasklet.Json_Flattener;
import com.t24extractor.tasklet.QueryTasklet;
import com.t24extractor.tasklet.StandardSelectionTasklet;
import com.t24extractor.tasklet.TableNameTasklet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tests de non-régression du pipeline d'extraction.
 *
 * Les tests unitaires existants valident chaque tasklet isolément. Ce test
 * couvre ce qu'ils ne peuvent pas voir : le CONTRAT entre les steps, qui
 * transitent uniquement par le JobExecutionContext (clés « Table_Name »,
 * « Type », « QUERY », « sysFieldOds », « COLUMNS », « SQL_QUERY »).
 *
 * Une régression typique ici — un step qui cesse d'écrire une clé, ou qui la
 * renomme — passe inaperçue en tests unitaires et casse le job en production.
 *
 * L'enchaînement reproduit celui de {@code customerFlow} :
 * TableName → StandardSelection → QueryBuilding → BI_SYNC → Manager → JSON_NORMALIZATION.
 */
class ExtractionPipelineRegressionTest {

    private static final String STANDARD_SELECTION_XML = """
            <row>
              <c4 m="1">A</c4>
              <c6 m="1">35</c6>
              <c10 m="1">S</c10>
              <c2 m="1">D</c2>
              <c3 m="1">1</c3>
              <c1 m="1">NAME</c1>
              <c4 m="2">A</c4>
              <c6 m="2">MAX</c6>
              <c10 m="2">M</c10>
              <c2 m="2">D</c2>
              <c3 m="2">2</c3>
              <c1 m="2">PHONES</c1>
            </row>
            """;

    private final TafjVocRepository tafjVocRepository = mock(TafjVocRepository.class);
    private final StandardSelectionRepository standardSelectionRepository = mock(StandardSelectionRepository.class);
    private final FileControlServiceImpl fileControlService = mock(FileControlServiceImpl.class);
    private final SchemaSourceRepository schemaSourceRepository = mock(SchemaSourceRepository.class);
    private final SchemaSourceAuditRepository schemaSourceAuditRepository = mock(SchemaSourceAuditRepository.class);
    private final MetaDataRepository metaDataRepository = mock(MetaDataRepository.class);
    private final ScheduledOpsRepository scheduledOpsRepository = mock(ScheduledOpsRepository.class);
    private final JdbcTemplate jdbcT24 = mock(JdbcTemplate.class);
    private final JdbcTemplate jdbcBI = mock(JdbcTemplate.class);

    private final BatchTestContext context = BatchTestContext.create();

    @AfterEach
    void clearSharedState() {
        StandardSelectionTasklet.getBlobViewFields().clear();
    }

    @Test
    void xmlFlowHandsOverEveryContextKeyDownTheChain() throws Exception {
        givenCustomerIsAnXmlTable();
        givenSchemaSourceKnows("NAME", "PHONES");

        // 1. TableName : résout le nom Oracle et le type de stockage.
        runTableNameStep();
        ExecutionContext jobContext = context.jobExecution().getExecutionContext();
        assertEquals(FBNK_CUSTOMER, jobContext.getString("Table_Name"),
                "Table_Name alimente le reader et le QueryTasklet");
        assertEquals('X', jobContext.get("Type"), "Type pilote le choix XML/BLOB");

        // 2. StandardSelection : produit le dictionnaire des champs T24.
        runStandardSelectionStep();
        assertTrue(jobContext.containsKey(BI_STANDARD_SELECTION),
                "sysFieldOds est la référence unique consommée par BI_SYNC puis JSON_NORMALIZATION");
        JSONObject selection = new JSONObject(jobContext.getString(BI_STANDARD_SELECTION));
        assertEquals("String35.c1", selection.getString("NAME"));
        assertEquals("StringMAX.c2", selection.getString("PHONES"), "un champ multivalué doit rester en MAX");
        // Table XML : aucune vue BLOB ne doit être produite.
        assertFalse(jobContext.containsKey("VIEW_QUERY"));

        // 3. QueryBuilding : construit la requête de lecture Oracle.
        runQueryStep();
        assertEquals("SELECT  RECID , XMLRECORD FROM FBNK_CUSTOMER ", jobContext.getString("QUERY"),
                "QUERY est lue par oracleXmlReader via #{jobExecutionContext['QUERY']}");

        // 4. BI_SYNC : dérive colonnes et INSERT du MÊME dictionnaire.
        runBiSyncStep(selection);
        assertEquals(List.of("_id", "ORIGINAL_ID", "NAME", "PHONES"), jobContext.get("COLUMNS"));
        assertEquals("INSERT INTO [CUSTOMER]([_id],[ORIGINAL_ID],[NAME],[PHONES]) VALUES (?,?,?,?);",
                jobContext.getString("SQL_QUERY"),
                "SQL_QUERY est injectée dans le JdbcBatchItemWriter");

        // 5. JSON_NORMALIZATION : relit sysFieldOds réécrit par BI_SYNC.
        when(jdbcBI.queryForObject(anyString(), eq(Integer.class), anyString(), anyString())).thenReturn(1);
        runNormalizationStep();
    }

    @Test
    void columnsAndInsertPlaceholdersStayAligned() throws Exception {
        givenCustomerIsAnXmlTable();
        givenSchemaSourceKnows("NAME", "PHONES");
        runTableNameStep();
        runStandardSelectionStep();

        ExecutionContext jobContext = context.jobExecution().getExecutionContext();
        runBiSyncStep(new JSONObject(jobContext.getString(BI_STANDARD_SELECTION)));

        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) jobContext.get("COLUMNS");
        String insert = jobContext.getString("SQL_QUERY");

        // Le writer positionne un paramètre par colonne : tout écart décale
        // silencieusement les valeurs insérées en base.
        assertEquals(columns.size(), insert.chars().filter(c -> c == '?').count(),
                "COLUMNS et les placeholders de SQL_QUERY doivent rester alignés");
    }

    @Test
    void columnActiveButAbsentFromStandardSelectionIsExcludedFromInsert() throws Exception {
        givenCustomerIsAnXmlTable();
        // BILL_REFERENCE est active dans SCHEMA_SOURCE mais absente du STANDARD.SELECTION.
        givenSchemaSourceKnows("NAME", "PHONES", "BILL_REFERENCE");
        runTableNameStep();
        runStandardSelectionStep();

        ExecutionContext jobContext = context.jobExecution().getExecutionContext();
        runBiSyncStep(new JSONObject(jobContext.getString(BI_STANDARD_SELECTION)));

        String insert = jobContext.getString("SQL_QUERY");
        assertFalse(insert.contains("BILL_REFERENCE"),
                "une colonne non mappée ferait échouer le writer : elle doit être exclue de l'INSERT");
        assertFalse(((List<?>) jobContext.get("COLUMNS")).contains("BILL_REFERENCE"));
    }

    @Test
    void unknownTableStopsTheFlowWithNewTableExitStatus() throws Exception {
        givenCustomerIsAnXmlTable();
        // Aucune ligne dans SCHEMA_SOURCE : la table n'a jamais été chargée.
        when(schemaSourceRepository.countByTableName(CUSTOMER)).thenReturn(0L);
        when(schemaSourceRepository.findSchemaSourceByTableNameAndEnabled(CUSTOMER, true)).thenReturn(List.of());
        runTableNameStep();
        runStandardSelectionStep();

        ExecutionContext jobContext = context.jobExecution().getExecutionContext();
        runBiSyncStep(new JSONObject(jobContext.getString(BI_STANDARD_SELECTION)));

        assertEquals(NEW_TABLE, context.jobExecution().getExitStatus().getExitCode());
        // Le flux s'arrête avant de produire un INSERT inexploitable.
        assertFalse(jobContext.containsKey("SQL_QUERY"));
    }

    @Test
    void partitionerGridMatchesTheKeyReadByTheReader() {
        Map<String, ExecutionContext> partitions = new MssqlPartitioner().partition(4);

        assertEquals(4, partitions.size());
        // oracleXmlReader lit #{stepExecutionContext['value']} : la clé et
        // l'étendue des valeurs conditionnent la clause MOD(ORA_HASH(ROWID), ?).
        for (int i = 0; i < 4; i++) {
            ExecutionContext partition = partitions.get("partition" + i);
            assertNotEquals(null, partition, "partition" + i);
            assertEquals(i, partition.getInt("value"));
        }
    }

    // ── Steps ────────────────────────────────────────────────────────────────

    private void runTableNameStep() throws Exception {
        TableNameTasklet tasklet = new TableNameTasklet(tafjVocRepository, fileControlService);
        tasklet.setT24TableName(CUSTOMER);
        tasklet.execute(context.contribution(), context.chunkContext());
    }

    private void runStandardSelectionStep() throws Exception {
        StandardSelectionTasklet tasklet = new StandardSelectionTasklet(
                jdbcBI, tafjVocRepository, standardSelectionRepository);
        tasklet.setT24TableName(CUSTOMER);
        tasklet.setOracleTableName(FBNK_CUSTOMER);
        tasklet.setTableType("X");
        tasklet.execute(context.contribution(), context.chunkContext());
    }

    private void runQueryStep() throws Exception {
        QueryTasklet tasklet = new QueryTasklet(jdbcT24);
        tasklet.setTableName(FBNK_CUSTOMER);
        tasklet.setChargement("Full");
        tasklet.execute(context.contribution(), context.chunkContext());
    }

    /**
     * Le conteneur injecte {@code ss} depuis
     * {@code #{jobExecutionContext['sysFieldOds']}} : on reproduit ici cette
     * injection à partir de la valeur réellement écrite par le step précédent.
     */
    private void runBiSyncStep(JSONObject standardSelection) throws Exception {
        BISyncTasklet tasklet = new BISyncTasklet(
                schemaSourceRepository, schemaSourceAuditRepository,
                metaDataRepository, scheduledOpsRepository, jdbcBI);
        tasklet.setT24TableName(CUSTOMER);
        tasklet.setColonneRef(null);
        tasklet.setSs(standardSelection);
        tasklet.execute(context.contribution(), context.chunkContext());
    }

    private void runNormalizationStep() throws Exception {
        Json_Flattener tasklet = new Json_Flattener(jdbcBI);
        tasklet.setTable(CUSTOMER);
        tasklet.execute(context.contribution(), context.chunkContext());
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private void givenCustomerIsAnXmlTable() {
        when(fileControlService.classification(CUSTOMER)).thenReturn("FBNK");
        when(tafjVocRepository.findTafjVocByRecid("FBNK.CUSTOMER"))
                .thenReturn(Optional.of(new TafjVoc("FBNK.CUSTOMER", "X", FBNK_CUSTOMER)));
        when(tafjVocRepository.findTafjVocByOrclFileName(FBNK_CUSTOMER))
                .thenReturn(Optional.of(new TafjVoc("FBNK.CUSTOMER", "X", FBNK_CUSTOMER)));

        StandardSelectionRecord record = new StandardSelectionRecord();
        record.setRecid(CUSTOMER);
        record.setXmlRecord(STANDARD_SELECTION_XML);
        when(standardSelectionRepository.findById(CUSTOMER)).thenReturn(Optional.of(record));

        when(jdbcT24.queryForObject(anyString(), eq(Long.class), any(), any())).thenReturn(10L);
    }

    private void givenSchemaSourceKnows(String... columns) {
        List<SchemaSource> schema = java.util.Arrays.stream(columns)
                .map(column -> new SchemaSource("id-" + column, CUSTOMER, column,
                        "PHONES".equals(column) ? "StringMAX" : "String",
                        true, false, "c1", "S", false))
                .toList();
        when(schemaSourceRepository.countByTableName(CUSTOMER)).thenReturn((long) schema.size());
        when(schemaSourceRepository.findSchemaSourceByTableNameAndEnabled(CUSTOMER, true)).thenReturn(schema);
        when(schemaSourceRepository.findAll(CUSTOMER)).thenReturn(schema);
        when(schemaSourceAuditRepository.findAllBySourceTableName(CUSTOMER)).thenReturn(List.of());
        // La table cible contient déjà les colonnes : pas d'auto-réparation.
        when(jdbcBI.queryForList(anyString(), eq(String.class)))
                .thenReturn(java.util.Arrays.asList(columns));
    }
}
