package com.t24extractor.configuration;

import com.t24extractor.preparedstatementsetter.ItemPrepStmtSetter;
import com.t24extractor.bi_sync.tasklet.BISyncTasklet;
import com.t24extractor.commons.Constants;
import com.t24extractor.decider.ExecutionFlowDecider;
import com.t24extractor.listeners.JobListener;
import com.t24extractor.model.T24Table;
import com.t24extractor.partitioner.MssqlPartitioner;
import com.t24extractor.processor.BlobFilterProcessor;
import com.t24extractor.processor.XMLFilterProcessor;
import com.t24extractor.processor.XMLProcessor;
import com.t24extractor.repository.bi_repos.MetaDataRepository;
import com.t24extractor.repository.bi_repos.ScheduledOpsRepository;
import com.t24extractor.repository.bi_repos.SchemaSourceAuditRepository;
import com.t24extractor.repository.bi_repos.SchemaSourceRepository;
import com.t24extractor.repository.t24_repo.StandardSelectionRepository;
import com.t24extractor.repository.t24_repo.TafjVocRepository;
import com.t24extractor.rowmapper.BlobRowMapper;
import com.t24extractor.rowmapper.XMLRowMapper;
import com.t24extractor.tasklet.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.partition.PartitionHandler;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.support.SynchronizedItemStreamReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.concurrent.Executors;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;

import static com.t24extractor.commons.Constants.*;


/**
 * Déclaration des tasklets, steps, flows et jobs Spring Batch.
 *
 * <h2>Trois tables, un même enchaînement</h2>
 * CUSTOMER, FUNDS.TRANSFER$HIS et AA.BILL.DETAILS suivent le même pipeline et
 * ne diffèrent que par quelques valeurs. Toute cette variabilité est décrite
 * une seule fois dans {@link TableDefinition} ; les fabriques privées
 * {@code build…} portent la logique commune, et les méthodes {@code @Bean} se
 * réduisent à une délégation.
 *
 * <p>Ajouter une table revient donc à déclarer une {@code TableDefinition} puis
 * les beans qui s'y rattachent, sans dupliquer de logique.
 *
 * <h2>Noms référencés ailleurs</h2>
 * Les noms déclarés ici sont utilisés en dur dans le reste du code : le job
 * {@code T24} est recherché par
 * {@link com.t24extractor.controller.JobLauncherController} pour arrêter les
 * extractions, et le nom du step worker doit correspondre au préfixe de
 * partition employé par son manager. Les renommer sans précaution casse le
 * pilotage sans provoquer d'erreur de compilation.
 *
 * <h2>Parallélisme</h2>
 * La lecture est partitionnée entre {@code workers.number} workers portés par
 * des threads virtuels ; chacun lit sa part via une clause de hachage sur le
 * ROWID Oracle.
 */
@Configuration
@ComponentScan(basePackages = "com.t24extractor")
@Import({BIDataSourceConfiguration.class, T24DataSourceConfiguration.class})
public class BatchConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(BatchConfiguration.class);

    /***************************************************************************
     *
     *  Définition centralisée des tables : TOUTE la variabilité entre
     *  CUSTOMER / FT$HIS / AA.BILL.DETAILS est décrite ici, une seule fois.
     *
     ***************************************************************************/

    /**
     * Tout ce qui distingue une table extraite d'une autre.
     *
     * <p>Une même table porte jusqu'à quatre noms différents selon l'étape :
     * son nom fonctionnel T24, la table Oracle réellement lue, le nom sous
     * lequel le processeur la reconnaît, et la table BI de destination. Ces noms
     * ne coïncident pas toujours — voir {@code processorOracleName}.
     *
     * <p>Le mode de chargement n'est pas stocké : il se déduit de la présence
     * d'une colonne de référence.
     *
     * @param t24Name             nom fonctionnel T24 ({@code CUSTOMER},
     *                            {@code FUNDS.TRANSFER$HIS}…), qui sert aussi de
     *                            clé dans SCHEMA_SOURCE une fois converti
     * @param oracleName          table Oracle interrogée pour le dictionnaire et
     *                            la lecture des données
     * @param processorOracleName nom transmis au {@link XMLProcessor}. Il peut
     *                            différer de {@code oracleName} : pour les tables
     *                            historisées, c'est ce nom qui doit contenir
     *                            {@code #HIS} pour que {@code ORIGINAL_ID} soit
     *                            amputé du numéro de version
     * @param flattenerName       table BI dont les champs multivalués sont
     *                            éclatés en satellites {@code TABLE#CHAMP}
     * @param columnRef           colonne de date pilotant l'incrémental ;
     *                            {@code null} impose un chargement complet
     * @param dateFormat          format de {@code columnRef} ({@code VARCHAR}
     *                            pour une date quotée) ; {@code null} si complet
     * @param lastDate            borne basse du chargement incrémental ;
     *                            {@code null} si complet
     */
    private record TableDefinition(
            String t24Name,             // Nom T24 (CUSTOMER, FUNDS.TRANSFER$HIS...)
            String oracleName,          // Table Oracle pour StandardSelection / Query
            String processorOracleName, // Table Oracle vue par le XMLProcessor
            String flattenerName,       // Nom de table pour Json_Flattener
            String columnRef,           // Colonne de référence (null => chargement Full)
            String dateFormat,          // Format de la colonne date (null si Full)
            String lastDate             // Dernière date (null si Full)
    ) {
        /**
         * @return {@code true} si la table se recharge par delta, {@code false}
         *         si elle est relue intégralement
         */
        boolean isIncremental() {
            return columnRef != null;
        }

        /**
         * @return le mode de chargement attendu par
         *         {@link com.t24extractor.tasklet.QueryTasklet}
         */
        String chargement() {
            return isIncremental() ? "Incremental" : "Full";
        }
    }

    /** Référentiel client : volumineux mais sans colonne de date exploitable, donc relu intégralement. */
    private static final TableDefinition CUSTOMER_DEF = new TableDefinition(
            CUSTOMER, FBNK_CUSTOMER, FBNK_CUSTOMER, CUSTOMER,
            null, null, null);

    /**
     * Historique des virements, chargé en incrémental sur la date de valeur au débit.
     *
     * <p>Seule table dont le nom vu par le processeur ({@code FBNK_FUNDS_TRANSFER#HIS})
     * diffère de la table lue ({@code FBNK_FUNDS_TRANSFER001}) : c'est ce qui
     * déclenche le calcul de {@code ORIGINAL_ID} propre aux tables historisées.
     */
    private static final TableDefinition FT_DEF = new TableDefinition(
            FUNDS_TRANSFER_HIS, "FBNK_FUNDS_TRANSFER001", "FBNK_FUNDS_TRANSFER#HIS", "FUNDS_TRANSFER#HIS",
            "DEBIT_VALUE_DATE", "VARCHAR", INITIAL_DATE);

    /** Échéances de facturation, chargées en incrémental sur la date de paiement. */
    private static final TableDefinition AA_BILL_DEF = new TableDefinition(
            AA_BILL_DETAILS, FBNK_BILL_DETAILS, FBNK_BILL_DETAILS, "AA_BILL_DETAILS",
            "PAYMENT_DATE", "VARCHAR", INITIAL_DATE);



    /**
     * Nombre de workers lisant Oracle en parallèle.
     *
     * <p>Sert à la fois de taille de grille pour le partitionnement et de
     * diviseur dans la clause de hachage du ROWID : les deux doivent rester
     * cohérents, sous peine de lire deux fois les mêmes lignes ou d'en omettre.
     */
    @Value("${workers.number}")
    private int workersNumber;

    /** Source Oracle (T24), lue par les readers. */
    private final DataSource dataSource;

    /** Cible SQL Server (BI), écrite par le writer. */
    private final DataSource biDataSource;

    /** Contrôle de clôture journalière, partagé par les trois flows. */
    private final CobStatusTasklet cobStatusTasklet;

    /**
     * @param cobStatusTasklet vérification de la clôture journalière, partagée
     *                         par les trois flows
     * @param dataSource       source Oracle (T24), en lecture
     * @param biDataSource     cible SQL Server (BI), en écriture
     */
    @Autowired
    public BatchConfiguration(
            CobStatusTasklet cobStatusTasklet,
            @Qualifier("t24") DataSource dataSource,
            @Qualifier("bi") DataSource biDataSource) {
        this.cobStatusTasklet = cobStatusTasklet;
        this.dataSource = dataSource;
        this.biDataSource = biDataSource;
    }

    /***************************************************************************
     *
     *  Fabriques privées : toute la logique commune, écrite UNE fois.
     *
     ***************************************************************************/

    /**
     * Tasklet de lecture du dictionnaire T24.
     *
     * <p>Le type de table est initialisé à {@code X} : c'est une valeur
     * d'amorçage, corrigée à l'exécution si TAFJ_VOC annonce un stockage BLOB.
     *
     * @param jdbcExecutor      accès Oracle
     * @param jdbcBatchExecutor accès BI, pour corriger le type en base
     * @param def               table traitée
     * @return le tasklet STANDARD.SELECTION configuré
     */
    private StandardSelectionTasklet buildStandardSelectionTasklet(
            JdbcTemplate jdbcExecutor,
            JdbcTemplate jdbcBatchExecutor,
            TafjVocRepository tafjVocRepository,
            StandardSelectionRepository standardSelectionRepository,
            TableDefinition def) {
        StandardSelectionTasklet tasklet =
                new StandardSelectionTasklet(jdbcBatchExecutor, tafjVocRepository, standardSelectionRepository);
        tasklet.setJdbcExecutor(jdbcExecutor);
        tasklet.setT24TableName(def.t24Name());
        tasklet.setTableType("X");
        tasklet.setOracleTableName(def.oracleName());
        return tasklet;
    }

    /**
     * @param def table traitée
     * @return le tasklet résolvant le nom Oracle et le mode de stockage
     */
    private TableNameTasklet buildTableNameTasklet(
            TafjVocRepository tafjVocRepository,
            FileControlServiceImpl fileControlService,
            TableDefinition def) {
        TableNameTasklet tasklet = new TableNameTasklet(tafjVocRepository, fileControlService);
        tasklet.setT24TableName(def.t24Name());
        return tasklet;
    }

    /**
     * Tasklet de construction de la requête de lecture.
     *
     * <p>Le mode de chargement est déduit de la définition : une table sans
     * colonne de référence est relue intégralement.
     *
     * @param jdbcTemplateT24 accès Oracle, utilisé pour le comptage qui décide
     *                        du partitionnement
     * @param def             table traitée
     * @return le tasklet de construction de requête
     */
    private QueryTasklet buildQueryTasklet(JdbcTemplate jdbcTemplateT24, TableDefinition def) {
        QueryTasklet tasklet = new QueryTasklet(jdbcTemplateT24);
        tasklet.setColumnRef(def.columnRef());
        tasklet.setDateFormat(def.dateFormat());
        tasklet.setTableName(def.oracleName());
        tasklet.setChargement(def.chargement());
        tasklet.setLastDate(def.lastDate());
        return tasklet;
    }

    /**
     * Tasklet d'alignement du schéma BI.
     *
     * @param ssMap        dictionnaire publié par l'étape STANDARD.SELECTION
     * @param jdbcTemplate accès BI, seule base modifiée
     * @param def          table traitée ; sa colonne de référence sera indexée
     * @return le tasklet BI_SYNC configuré
     */
    private BISyncTasklet buildBiSyncTasklet(
            JSONObject ssMap,
            SchemaSourceRepository schemaSourceRepository,
            SchemaSourceAuditRepository schemaSourceAuditRepository,
            MetaDataRepository metaDataRepository,
            ScheduledOpsRepository scheduledOpsRepository,
            JdbcTemplate jdbcTemplate,
            TableDefinition def) {
        BISyncTasklet tasklet = new BISyncTasklet(
                schemaSourceRepository,
                schemaSourceAuditRepository,
                metaDataRepository,
                scheduledOpsRepository,
                jdbcTemplate);
        tasklet.setSs(ssMap);
        tasklet.setT24TableName(def.t24Name());
        tasklet.setColonneRef(def.columnRef());
        return tasklet;
    }

    /**
     * @param def table traitée ; c'est {@code processorOracleName} qui est
     *            transmis, et non la table réellement lue
     * @return le processeur de décodage XMLRECORD
     */
    private XMLProcessor buildXmlProcessor(TableDefinition def) {
        XMLProcessor processor = new XMLProcessor();
        processor.setOracleTableName(def.processorOracleName());
        return processor;
    }

    /**
     * @param jdbcTemplate accès BI
     * @param def          table traitée ; le nom employé est celui de la table
     *                     BI, préfixe des tables satellites
     * @return le tasklet de normalisation des champs multivalués
     */
    private Json_Flattener buildFlattener(JdbcTemplate jdbcTemplate, TableDefinition def) {
        Json_Flattener tasklet = new Json_Flattener(jdbcTemplate);
        tasklet.setTable(def.flattenerName());
        return tasklet;
    }

    /**
     * Step mono-tâche.
     *
     * @param name               nom du step ; il identifie l'étape dans les
     *                           métadonnées Spring Batch et au redémarrage
     * @param transactionManager gestionnaire de la base écrite par l'étape —
     *                           Oracle pour la préparation, BI pour l'écriture
     * @return le step construit
     */
    private Step buildTaskletStep(String name, Tasklet tasklet,
                                  JobRepository jobRepository,
                                  PlatformTransactionManager transactionManager) {
        return new StepBuilder(name, jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    /**
     * Step manager : découpe le travail et le distribue aux workers.
     *
     * @param name       nom du step manager
     * @param workerName préfixe des partitions ; il doit être identique au nom
     *                   du step worker, sans quoi les partitions ne sont pas
     *                   résolues
     * @param handler    exécuteur des partitions
     * @return le step manager construit
     */
    private Step buildManagerStep(String name, String workerName,
                                  PartitionHandler handler, JobRepository jobRepository) {
        return new StepBuilder(name, jobRepository)
                .partitioner(workerName, partitioner())
                .partitionHandler(handler)
                .build();
    }

    /**
     * Step worker : lit, transforme et écrit un lot d'enregistrements.
     *
     * <p>Les arguments passés à {@code oracleXmlReader} sont ignorés : le bean
     * étant de portée step, le conteneur substitue un proxy qui résoudra la
     * requête et l'index de partition à l'exécution.
     *
     * @param name               nom du step, à faire correspondre au préfixe de
     *                           partition du manager
     * @param processor          décodeur propre à la table traitée
     * @param itemWriter         writer alimenté par l'INSERT de BI_SYNC
     * @param transactionManager gestionnaire de la base BI
     * @return le step worker construit
     */
    private Step buildWorkerStep(String name,
                                 XMLProcessor processor,
                                 JdbcBatchItemWriter<com.alibaba.fastjson2.JSONObject> itemWriter,
                                 JobRepository jobRepository,
                                 PlatformTransactionManager transactionManager) {
        return new StepBuilder(name, jobRepository)
                .<T24Table, com.alibaba.fastjson2.JSONObject>chunk(CHUNKS_NUMBER)
                .transactionManager(transactionManager)
                .reader(oracleXmlReader(null, 0))
                .processor(processor)
                .writer(itemWriter)
                .build();
    }

    /**
     * Exécuteur de partitions dimensionné par {@code workers.number}.
     *
     * @param workerStep step exécuté par chaque partition
     * @return le gestionnaire de partitions
     * @throws Exception si la configuration du gestionnaire est incomplète
     */
    private PartitionHandler buildPartitionHandler(Step workerStep) throws Exception {
        TaskExecutorPartitionHandler handler = new TaskExecutorPartitionHandler();
        handler.setTaskExecutor(taskExecutor());
        handler.setGridSize(workersNumber);
        handler.setStep(workerStep);
        handler.afterPropertiesSet();
        return handler;
    }

    /**
     * Enchaînement complet d'extraction d'une table.
     *
     * <p>L'ordre est contraint par les données transmises d'étape en étape :
     * chacune consomme ce que la précédente a publié dans le contexte
     * d'exécution. Il ne peut pas être réarrangé.
     *
     * @param name nom du flow, repris comme nom de table métier
     * @return le flow construit
     * @see com.t24extractor.tasklet
     */
    private Flow buildTableFlow(String name, Step cobStatus, Step tableNameStep,
                                Step standardSelectionStep, Step queryBuildingStep,
                                Step biSyncStep, Step managerStep, Step normalizationStep) {
        return new FlowBuilder<SimpleFlow>(name)
                .start(cobStatus)
                .next(tableNameStep)
                .next(standardSelectionStep)
                .next(queryBuildingStep)
                .next(biSyncStep)
                .next(managerStep)
                .next(normalizationStep)
                .build();
    }

    /***************************************************************************
     *
     *  Listeners
     *
     ***************************************************************************/

    /**
     * @param jdbcTemplate accès BI
     * @return l'observateur du cycle de vie des jobs
     */
    @Bean
    public JobExecutionListener jobListener(@Qualifier("jdbcBI") JdbcTemplate jdbcTemplate) {
        return new JobListener(jdbcTemplate);
    }

    /***************************************************************************
     *
     *  Tasklets (beans fins : 1 ligne chacun, délèguent aux fabriques)
     *
     ***************************************************************************/

    /** @return le dictionnaire CUSTOMER, lu depuis {@code FBNK_CUSTOMER} */
    @Bean
    public StandardSelectionTasklet standardSelectionCustomer(
            @Qualifier("jdbcTemplateT24") JdbcTemplate jdbcExecutor,
            @Qualifier("jdbcBI") JdbcTemplate jdbcBatchExecutor,
            TafjVocRepository tafjVocRepository,
            StandardSelectionRepository standardSelectionRepository) {
        return buildStandardSelectionTasklet(jdbcExecutor, jdbcBatchExecutor,
                tafjVocRepository, standardSelectionRepository, CUSTOMER_DEF);
    }

    /** @return le dictionnaire FUNDS.TRANSFER$HIS, lu depuis {@code FBNK_FUNDS_TRANSFER001} */
    @Bean
    public StandardSelectionTasklet standardSelectionFT(
            @Qualifier("jdbcTemplateT24") JdbcTemplate jdbcExecutor,
            @Qualifier("jdbcBI") JdbcTemplate jdbcBatchExecutor,
            TafjVocRepository tafjVocRepository,
            StandardSelectionRepository standardSelectionRepository) {
        return buildStandardSelectionTasklet(jdbcExecutor, jdbcBatchExecutor,
                tafjVocRepository, standardSelectionRepository, FT_DEF);
    }

    /** @return le dictionnaire AA.BILL.DETAILS, lu depuis {@code FBNK_AA_BILL_DETAILS} */
    @Bean
    public StandardSelectionTasklet standardSelectionAABill(
            @Qualifier("jdbcTemplateT24") JdbcTemplate jdbcExecutor,
            @Qualifier("jdbcBI") JdbcTemplate jdbcBatchExecutor,
            TafjVocRepository tafjVocRepository,
            StandardSelectionRepository standardSelectionRepository) {
        return buildStandardSelectionTasklet(jdbcExecutor, jdbcBatchExecutor,
                tafjVocRepository, standardSelectionRepository, AA_BILL_DEF);
    }

    /** @return la résolution du nom Oracle de CUSTOMER */
    @Bean
    public TableNameTasklet tableNameCustomer(TafjVocRepository tafjVocRepository, FileControlServiceImpl fileControlService) {
        return buildTableNameTasklet(tafjVocRepository, fileControlService, CUSTOMER_DEF);
    }

    /** @return la résolution du nom Oracle de FUNDS.TRANSFER$HIS */
    @Bean
    public TableNameTasklet tableNameFT(TafjVocRepository tafjVocRepository, FileControlServiceImpl fileControlService) {
        return buildTableNameTasklet(tafjVocRepository, fileControlService, FT_DEF);
    }

    /** @return la résolution du nom Oracle de AA.BILL.DETAILS */
    @Bean
    public TableNameTasklet tableNameAABill(TafjVocRepository tafjVocRepository, FileControlServiceImpl fileControlService) {
        return buildTableNameTasklet(tafjVocRepository, fileControlService, AA_BILL_DEF);
    }

    /** @return la requête CUSTOMER — chargement complet, sans borne de date */
    @Bean
    public QueryTasklet queryCustomerTasklet(@Qualifier("jdbcTemplateT24") JdbcTemplate jdbcTemplateT24) {
        return buildQueryTasklet(jdbcTemplateT24, CUSTOMER_DEF);
    }

    /** @return la requête FUNDS.TRANSFER$HIS — incrémental sur {@code DEBIT_VALUE_DATE} */
    @Bean
    public QueryTasklet queryFtTasklet(@Qualifier("jdbcTemplateT24") JdbcTemplate jdbcTemplateT24) {
        return buildQueryTasklet(jdbcTemplateT24, FT_DEF);
    }

    /** @return la requête AA.BILL.DETAILS — incrémental sur {@code PAYMENT_DATE} */
    @Bean
    public QueryTasklet queryAABillTasklet(@Qualifier("jdbcTemplateT24") JdbcTemplate jdbcTemplateT24) {
        return buildQueryTasklet(jdbcTemplateT24, AA_BILL_DEF);
    }

    /***************************************************************************
     *
     *  XML Reader
     *
     ***************************************************************************/

    /**
     * Lecteur des tables stockées en XML.
     *
     * <p>Le reader est synchronisé car partagé entre workers. Lorsque la requête
     * est partitionnée, les deux paramètres attendus sont le nombre de workers
     * et l'index de la partition courante.
     *
     * @param query requête publiée par {@link com.t24extractor.tasklet.QueryTasklet}
     * @param value index de la partition traitée
     * @return le lecteur d'enregistrements T24
     */
    @Bean
    @StepScope
    public SynchronizedItemStreamReader<T24Table>
    oracleXmlReader(@Value("#{jobExecutionContext['QUERY']}") String query,
                    @Value("#{stepExecutionContext['value']}") int value) {

        LOG.info("[Reader Query] : {}", query);

        JdbcCursorItemReader<T24Table> reader = new JdbcCursorItemReader<>(dataSource, query, xmlRowMapper());
        reader.setFetchSize(FETCH_SIZE);
        reader.setConnectionAutoCommit(false);
        if (query.contains("ABS")) {
            reader.setPreparedStatementSetter(ps -> {
                ps.setInt(1, workersNumber);
                ps.setInt(2, value);
            });
        }
        return new SynchronizedItemStreamReader<>(reader);
    }

    /** @return le mappeur des lignes {@code (RECID, XMLRECORD)} */
    @Bean
    public XMLRowMapper xmlRowMapper() {
        return new XMLRowMapper();
    }

    /***************************************************************************
     *
     *  BLOB/CLOB Reader
     *
     ***************************************************************************/

    /**
     * Lecteur des tables stockées en BLOB, au travers de la vue éclatée.
     *
     * <p>Le partitionnement se fait ici par plage de numéros de ligne, la vue
     * exposant un {@code rowNumber}.
     *
     * @param query    vue publiée par {@link com.t24extractor.tasklet.StandardSelectionTasklet}
     * @param minValue borne basse de la plage attribuée au worker
     * @param maxValue borne haute de la plage attribuée au worker
     * @return le lecteur de lignes de vue
     */
    @Bean
    @StepScope
    public SynchronizedItemStreamReader<HashMap<String, Object>>
    oracleBlobReader(@Value("#{jobExecutionContext['VIEW_QUERY']}") String query,
                     @Value("#{stepExecutionContext['minValue']}") Long minValue,
                     @Value("#{stepExecutionContext['maxValue']}") Long maxValue) {
        String readerQuery = String.format(SELECT_VIEW_ROW_RANGE_TPL, query, minValue, maxValue);

        LOG.trace("[minValue - maxValue] : {} -> {}", minValue, maxValue);
        LOG.trace("[Reader Query] : \n{}", readerQuery);

        JdbcCursorItemReader<HashMap<String, Object>> reader = new JdbcCursorItemReader<>(dataSource, readerQuery, blobRowMapper());
        reader.setFetchSize(FETCH_SIZE);
        reader.setConnectionAutoCommit(false);
        return new SynchronizedItemStreamReader<>(reader);
    }

    /** @return le mappeur des lignes de vue BLOB, aux colonnes connues à l'exécution */
    @Bean
    @StepScope
    public BlobRowMapper blobRowMapper() {
        return new BlobRowMapper();
    }

    /***************************************************************************
     *
     *  Steps
     *
     ***************************************************************************/

    /** @return le step {@code Standard.Selection.CUSTOMER} */
    @Bean
    public Step standardSelectionCustomerStep(@Qualifier("standardSelectionCustomer") StandardSelectionTasklet tasklet, JobRepository jobRepository, @Qualifier("t24TransactionManager") PlatformTransactionManager transactionManager) {
        return buildTaskletStep("Standard.Selection.CUSTOMER", tasklet, jobRepository, transactionManager);
    }

    /** @return le step {@code Standard.Selection.FUNDS.TRANSFER} */
    @Bean
    public Step standardSelectionFTStep(@Qualifier("standardSelectionFT") StandardSelectionTasklet tasklet, JobRepository jobRepository, @Qualifier("t24TransactionManager") PlatformTransactionManager transactionManager) {
        return buildTaskletStep("Standard.Selection.FUNDS.TRANSFER", tasklet, jobRepository, transactionManager);
    }

    /** @return le step {@code Standard.Selection.AA.BILL.DETAILS} */
    @Bean
    public Step standardSelectionAABillStep(@Qualifier("standardSelectionAABill") StandardSelectionTasklet tasklet, JobRepository jobRepository, @Qualifier("t24TransactionManager") PlatformTransactionManager transactionManager) {
        return buildTaskletStep("Standard.Selection.AA.BILL.DETAILS", tasklet, jobRepository, transactionManager);
    }

    /** @return le step {@code TableName.CUSTOMER} */
    @Bean
    public Step tableNameCustomerStep(@Qualifier("tableNameCustomer") TableNameTasklet tasklet, JobRepository jobRepository, @Qualifier("t24TransactionManager") PlatformTransactionManager transactionManager) {
        return buildTaskletStep("TableName.CUSTOMER", tasklet, jobRepository, transactionManager);
    }

    /** @return le step {@code TableName.FT$HIS} */
    @Bean
    public Step tableNameFTStep(@Qualifier("tableNameFT") TableNameTasklet tasklet, JobRepository jobRepository, @Qualifier("t24TransactionManager") PlatformTransactionManager transactionManager) {
        return buildTaskletStep("TableName.FT$HIS", tasklet, jobRepository, transactionManager);
    }

    /** @return le step {@code TableName.AA.BILL.DETAILS} */
    @Bean
    public Step tableNameAABillStep(@Qualifier("tableNameAABill") TableNameTasklet tasklet, JobRepository jobRepository, @Qualifier("t24TransactionManager") PlatformTransactionManager transactionManager) {
        return buildTaskletStep("TableName.AA.BILL.DETAILS", tasklet, jobRepository, transactionManager);
    }

    /**
     * @return le step de construction de requête CUSTOMER
     * @implNote les trois steps de construction partagent le nom
     *           {@code QueryBuildingStep} : chaque job étant indépendant, la
     *           collision est sans effet.
     */
    @Bean
    @JobScope
    public Step cusQueryBuildingStep(@Qualifier("queryCustomerTasklet") QueryTasklet tasklet, JobRepository jobRepository, @Qualifier("t24TransactionManager") PlatformTransactionManager transactionManager) {
        return buildTaskletStep(QUERY_BUILDING_STEP, tasklet, jobRepository, transactionManager);
    }

    /** @return le step de construction de requête FUNDS.TRANSFER$HIS */
    @Bean
    @JobScope
    public Step ftQueryBuildingStep(@Qualifier("queryFtTasklet") QueryTasklet tasklet, JobRepository jobRepository, @Qualifier("t24TransactionManager") PlatformTransactionManager transactionManager) {
        return buildTaskletStep(QUERY_BUILDING_STEP, tasklet, jobRepository, transactionManager);
    }

    /** @return le step de construction de requête AA.BILL.DETAILS */
    @Bean
    @JobScope
    public Step aaQueryBuildingStep(@Qualifier("queryAABillTasklet") QueryTasklet tasklet, JobRepository jobRepository, @Qualifier("t24TransactionManager") PlatformTransactionManager transactionManager) {
        return buildTaskletStep(QUERY_BUILDING_STEP, tasklet, jobRepository, transactionManager);
    }

    /**********************
     *
     *  Managers Steps
     *
     **********************/

    /** @return le manager CUSTOMER, distribuant le travail aux {@code cusWorker} */
    @Bean
    @JobScope
    public Step cusManager(PartitionHandler cusXMLPartitionHandler, JobRepository jobRepository) {
        return buildManagerStep(CUSTOMER, "cusWorker", cusXMLPartitionHandler, jobRepository);
    }

    /** @return le manager FUNDS.TRANSFER$HIS, distribuant le travail aux {@code ftWorker} */
    @Bean
    @JobScope
    public Step ftManager(PartitionHandler ftXMLPartitionHandler, JobRepository jobRepository) {
        return buildManagerStep(FUNDS_TRANSFER_HIS, "ftWorker", ftXMLPartitionHandler, jobRepository);
    }

    /** @return le manager AA.BILL.DETAILS, distribuant le travail aux {@code aaWorker} */
    @Bean
    @JobScope
    public Step aaManager(PartitionHandler aaXMLPartitionHandler, JobRepository jobRepository) {
        return buildManagerStep(AA_BILL_DETAILS, "aaWorker", aaXMLPartitionHandler, jobRepository);
    }

    /**
     * @return le partitionneur, partagé par les trois managers : il n'attribue
     *         qu'un index par worker, sans état propre à une table
     */
    @Bean
    public MssqlPartitioner partitioner() {
        return new MssqlPartitioner();
    }

    /**********************
     *
     *  Workers Steps
     *
     **********************/

    /** @return le worker CUSTOMER ; son nom doit rester aligné sur {@link #cusManager} */
    @Bean
    public Step cusWorker(JdbcBatchItemWriter<com.alibaba.fastjson2.JSONObject> itemWriter, JobRepository jobRepository, @Qualifier("biTransactionManager") PlatformTransactionManager transactionManager) {
        return buildWorkerStep("cusWorker", cusXmlProcessor(), itemWriter, jobRepository, transactionManager);
    }

    /** @return le worker FUNDS.TRANSFER$HIS ; son nom doit rester aligné sur {@link #ftManager} */
    @Bean
    public Step ftWorker(JdbcBatchItemWriter<com.alibaba.fastjson2.JSONObject> itemWriter, JobRepository jobRepository, @Qualifier("biTransactionManager") PlatformTransactionManager transactionManager) {
        return buildWorkerStep("ftWorker", ftXmlProcessor(), itemWriter, jobRepository, transactionManager);
    }

    /** @return le worker AA.BILL.DETAILS ; son nom doit rester aligné sur {@link #aaManager} */
    @Bean
    public Step aaWorker(JdbcBatchItemWriter<com.alibaba.fastjson2.JSONObject> itemWriter, JobRepository jobRepository, @Qualifier("biTransactionManager") PlatformTransactionManager transactionManager) {
        return buildWorkerStep("aaWorker", aaBillXmlProcessor(), itemWriter, jobRepository, transactionManager);
    }

    /***************************************************************************
     *
     *  Processors
     *
     ***************************************************************************/

    /** @return le filtre écartant les enregistrements XML déjà extraits (reprise) */
    @Bean
    @StepScope
    public XMLFilterProcessor xmlFilterProcessor() {
        return new XMLFilterProcessor();
    }

    /** @return le décodeur XMLRECORD de CUSTOMER */
    @Bean
    @StepScope
    public XMLProcessor cusXmlProcessor() {
        return buildXmlProcessor(CUSTOMER_DEF);
    }

    /**
     * @return le décodeur XMLRECORD de FUNDS.TRANSFER$HIS ; le nom {@code #HIS}
     *         qui lui est passé déclenche le calcul de {@code ORIGINAL_ID}
     */
    @Bean
    @StepScope
    public XMLProcessor ftXmlProcessor() {
        return buildXmlProcessor(FT_DEF);
    }

    /** @return le décodeur XMLRECORD de AA.BILL.DETAILS */
    @Bean
    @StepScope
    public XMLProcessor aaBillXmlProcessor() {
        return buildXmlProcessor(AA_BILL_DEF);
    }

    /** @return le filtre écartant les lignes BLOB déjà extraites (reprise) */
    @Bean
    @StepScope
    public BlobFilterProcessor blobFilterProcessor() {
        return new BlobFilterProcessor();
    }

    /***************************************************************************
     *
     *  Handlers
     *
     ***************************************************************************/

    /**
     * @param cusWorker step exécuté par chaque partition
     * @return l'exécuteur de partitions CUSTOMER
     * @throws Exception si le gestionnaire est mal configuré
     */
    @Bean
    public PartitionHandler cusXMLPartitionHandler(Step cusWorker) throws Exception {
        return buildPartitionHandler(cusWorker);
    }

    /**
     * @param ftWorker step exécuté par chaque partition
     * @return l'exécuteur de partitions FUNDS.TRANSFER$HIS
     * @throws Exception si le gestionnaire est mal configuré
     */
    @Bean
    public PartitionHandler ftXMLPartitionHandler(Step ftWorker) throws Exception {
        return buildPartitionHandler(ftWorker);
    }

    /**
     * @param aaWorker step exécuté par chaque partition
     * @return l'exécuteur de partitions AA.BILL.DETAILS
     * @throws Exception si le gestionnaire est mal configuré
     */
    @Bean
    public PartitionHandler aaXMLPartitionHandler(Step aaWorker) throws Exception {
        return buildPartitionHandler(aaWorker);
    }

    /**
     * @return le step de contrôle de la clôture journalière, première étape
     *         commune aux trois flows
     */
    @Bean
    public Step cobStatus(JobRepository jobRepository, @Qualifier("t24TransactionManager") PlatformTransactionManager transactionManager) {
        return buildTaskletStep(Constants.STEP_COB_STATUS, cobStatusTasklet, jobRepository, transactionManager);
    }

    /***************************************************************************
     *
     *  BI Sync
     *
     ***************************************************************************/

    /** @return le step {@code BI_SYNC} de CUSTOMER */
    @Bean
    @JobScope
    public Step bISyncCus(BISyncTasklet biSyncCusTasklet, JobRepository jobRepository, @Qualifier("biTransactionManager") PlatformTransactionManager transactionManager) {
        return buildTaskletStep(Constants.BI_SYNC, biSyncCusTasklet, jobRepository, transactionManager);
    }

    /** @return le step {@code BI_SYNC} de FUNDS.TRANSFER$HIS */
    @Bean
    @JobScope
    public Step bISyncFt(BISyncTasklet biSyncFtTasklet, JobRepository jobRepository, @Qualifier("biTransactionManager") PlatformTransactionManager transactionManager) {
        return buildTaskletStep(Constants.BI_SYNC, biSyncFtTasklet, jobRepository, transactionManager);
    }

    /** @return le step {@code BI_SYNC} de AA.BILL.DETAILS */
    @Bean
    @JobScope
    public Step bISyncAaBill(BISyncTasklet biSyncAaBillTasklet, JobRepository jobRepository, @Qualifier("biTransactionManager") PlatformTransactionManager transactionManager) {
        return buildTaskletStep(Constants.BI_SYNC, biSyncAaBillTasklet, jobRepository, transactionManager);
    }

    /**
     * @param ssMap dictionnaire résolu à l'exécution depuis le contexte de job
     * @return le tasklet BI_SYNC de CUSTOMER, sans colonne à indexer
     */
    @Bean
    @StepScope
    public BISyncTasklet biSyncCusTasklet(
            @Value("#{jobExecutionContext['" + Constants.BI_STANDARD_SELECTION + "']}") JSONObject ssMap,
            SchemaSourceRepository schemaSourceRepository,
            SchemaSourceAuditRepository schemaSourceAuditRepository,
            MetaDataRepository metaDataRepository,
            ScheduledOpsRepository scheduledOpsRepository,
            @Qualifier("jdbcBI") JdbcTemplate jdbcTemplate) {
        return buildBiSyncTasklet(ssMap, schemaSourceRepository, schemaSourceAuditRepository,
                metaDataRepository, scheduledOpsRepository, jdbcTemplate, CUSTOMER_DEF);
    }

    /**
     * @param ssMap dictionnaire résolu à l'exécution depuis le contexte de job
     * @return le tasklet BI_SYNC de FUNDS.TRANSFER$HIS, indexé sur {@code DEBIT_VALUE_DATE}
     */
    @Bean
    @StepScope
    public BISyncTasklet biSyncFtTasklet(
            @Value("#{jobExecutionContext['" + Constants.BI_STANDARD_SELECTION + "']}") JSONObject ssMap,
            SchemaSourceRepository schemaSourceRepository,
            SchemaSourceAuditRepository schemaSourceAuditRepository,
            MetaDataRepository metaDataRepository,
            ScheduledOpsRepository scheduledOpsRepository,
            @Qualifier("jdbcBI") JdbcTemplate jdbcTemplate) {
        return buildBiSyncTasklet(ssMap, schemaSourceRepository, schemaSourceAuditRepository,
                metaDataRepository, scheduledOpsRepository, jdbcTemplate, FT_DEF);
    }

    /**
     * @param ssMap dictionnaire résolu à l'exécution depuis le contexte de job
     * @return le tasklet BI_SYNC de AA.BILL.DETAILS, indexé sur {@code PAYMENT_DATE}
     */
    @Bean
    @StepScope
    public BISyncTasklet biSyncAaBillTasklet(
            @Value("#{jobExecutionContext['" + Constants.BI_STANDARD_SELECTION + "']}") JSONObject ssMap,
            SchemaSourceRepository schemaSourceRepository,
            SchemaSourceAuditRepository schemaSourceAuditRepository,
            MetaDataRepository metaDataRepository,
            ScheduledOpsRepository scheduledOpsRepository,
            @Qualifier("jdbcBI") JdbcTemplate jdbcTemplate) {
        return buildBiSyncTasklet(ssMap, schemaSourceRepository, schemaSourceAuditRepository,
                metaDataRepository, scheduledOpsRepository, jdbcTemplate, AA_BILL_DEF);
    }

    /***************************************************************************
     *
     *  Json Normalization
     *
     ***************************************************************************/

    /** @return la normalisation des satellites {@code CUSTOMER#…} */
    @Bean
    @StepScope
    public Json_Flattener normalizationCusTasklet(@Qualifier("jdbcBI") JdbcTemplate jdbcTemplate) {
        return buildFlattener(jdbcTemplate, CUSTOMER_DEF);
    }

    /** @return la normalisation des satellites {@code FUNDS_TRANSFER#HIS#…} */
    @Bean
    @StepScope
    public Json_Flattener normalizationFtTasklet(@Qualifier("jdbcBI") JdbcTemplate jdbcTemplate) {
        return buildFlattener(jdbcTemplate, FT_DEF);
    }

    /** @return la normalisation des satellites {@code AA_BILL_DETAILS#…} */
    @Bean
    @StepScope
    public Json_Flattener normalizationAaBillTasklet(@Qualifier("jdbcBI") JdbcTemplate jdbcTemplate) {
        return buildFlattener(jdbcTemplate, AA_BILL_DEF);
    }

    /** @return le step {@code JSON_NORMALIZATION} de CUSTOMER */
    @Bean
    @JobScope
    public Step jsonNormalizationCusStep(JobRepository jobRepository, @Qualifier("biTransactionManager") PlatformTransactionManager transactionManager, Json_Flattener normalizationCusTasklet) {
        return buildTaskletStep(JSON_NORMALIZATION, normalizationCusTasklet, jobRepository, transactionManager);
    }

    /** @return le step {@code JSON_NORMALIZATION} de FUNDS.TRANSFER$HIS */
    @Bean
    @JobScope
    public Step jsonNormalizationFtStep(JobRepository jobRepository, @Qualifier("biTransactionManager") PlatformTransactionManager transactionManager, Json_Flattener normalizationFtTasklet) {
        return buildTaskletStep(JSON_NORMALIZATION, normalizationFtTasklet, jobRepository, transactionManager);
    }

    /** @return le step {@code JSON_NORMALIZATION} de AA.BILL.DETAILS */
    @Bean
    @JobScope
    public Step jsonNormalizationAaBillStep(JobRepository jobRepository, @Qualifier("biTransactionManager") PlatformTransactionManager transactionManager, Json_Flattener normalizationAaBillTasklet) {
        return buildTaskletStep(JSON_NORMALIZATION, normalizationAaBillTasklet, jobRepository, transactionManager);
    }

    /**
     * Exécuteur des workers de partition.
     *
     * <p>Threads virtuels : les workers passent l'essentiel de leur temps en
     * attente d'entrées-sorties JDBC, ce qui rend le coût d'un thread noyau par
     * worker inutile.
     *
     * @return l'exécuteur asynchrone des partitions
     */
    @Bean
    public AsyncTaskExecutor taskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }

    /***************************************************************************
     *
     *  Writer
     *
     ***************************************************************************/

    /**
     * @return le lieur de paramètres, qui suit l'ordre de la liste
     *         {@code COLUMNS} publiée par BI_SYNC
     */
    @Bean
    @StepScope
    public ItemPrepStmtSetter itm() {
        return new ItemPrepStmtSetter();
    }

    /**
     * Writer alimentant la table BI.
     *
     * @param query INSERT publié par l'étape BI_SYNC, dont les paramètres sont
     *              positionnés par {@link ItemPrepStmtSetter}
     * @return le writer par lots
     */
    @Bean
    @StepScope
    public JdbcBatchItemWriter<com.alibaba.fastjson2.JSONObject> itemWriter(@Value("#{jobExecutionContext['SQL_QUERY']}") String query) {
        JdbcBatchItemWriter<com.alibaba.fastjson2.JSONObject> writer = new JdbcBatchItemWriter<>();
        writer.setDataSource(biDataSource);
        writer.setSql(query);
        writer.setItemPreparedStatementSetter(itm());
        return writer;
    }

    /***************************************************************************
     *
     *  Flows et Jobs
     *
     ***************************************************************************/

    /** @return l'enchaînement complet d'extraction de CUSTOMER */
    @Bean
    public Flow customerFlow(Step cobStatus, Step tableNameCustomerStep, Step standardSelectionCustomerStep, Step cusQueryBuildingStep, Step bISyncCus, Step cusManager, Step jsonNormalizationCusStep) {
        return buildTableFlow(CUSTOMER, cobStatus, tableNameCustomerStep, standardSelectionCustomerStep,
                cusQueryBuildingStep, bISyncCus, cusManager, jsonNormalizationCusStep);
    }

    /** @return l'enchaînement complet d'extraction de AA.BILL.DETAILS */
    @Bean
    public Flow aaBillFlow(Step cobStatus, Step tableNameAABillStep, Step standardSelectionAABillStep, Step aaQueryBuildingStep, Step bISyncAaBill, Step aaManager, Step jsonNormalizationAaBillStep) {
        return buildTableFlow(AA_BILL_DETAILS, cobStatus, tableNameAABillStep, standardSelectionAABillStep,
                aaQueryBuildingStep, bISyncAaBill, aaManager, jsonNormalizationAaBillStep);
    }

    /** @return l'enchaînement complet d'extraction de FUNDS.TRANSFER$HIS */
    @Bean
    public Flow ftFlow(Step cobStatus, Step tableNameFTStep, Step standardSelectionFTStep, Step ftQueryBuildingStep, Step bISyncFt, Step ftManager, Step jsonNormalizationFtStep) {
        return buildTableFlow(FUNDS_TRANSFER_HIS, cobStatus, tableNameFTStep, standardSelectionFTStep,
                ftQueryBuildingStep, bISyncFt, ftManager, jsonNormalizationFtStep);
    }

    /**
     * Job global : les trois tables, l'une après l'autre.
     *
     * <p>Le nom {@code T24} est recherché en dur par
     * {@link com.t24extractor.controller.JobLauncherController} pour arrêter
     * l'extraction en cours. Le renommer rendrait l'arrêt inopérant, sans erreur.
     *
     * @return le job d'extraction complète
     */
    @Bean(name = "t24Job")
    public Job job(Flow ftFlow, Flow customerFlow, Flow aaBillFlow, JobRepository jobRepository) {
        return new JobBuilder("T24", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(customerFlow)
                .next(aaBillFlow)
                .next(ftFlow)
                .end()
                .build();
    }

    /**
     * Job de découverte de schéma, sans extraction de données.
     *
     * <p>N'enchaîne que les étapes de préparation : il alimente SCHEMA_SOURCE et
     * crée les tables BI, ce qui permet de choisir les colonnes à extraire avant
     * de lancer un premier chargement. Il omet délibérément le contrôle de
     * clôture, la lecture et la normalisation.
     *
     * @return le job de chargement de schéma
     */
    @Bean("loadSchema")
    public Job loadJob(Step tableNameCustomerStep, Step standardSelectionCustomerStep, Step cusQueryBuildingStep, Step bISyncCus, Step tableNameAABillStep, Step standardSelectionAABillStep, Step aaQueryBuildingStep, Step bISyncAaBill, Step tableNameFTStep, Step standardSelectionFTStep, Step ftQueryBuildingStep, Step bISyncFt, JobRepository jobRepository) {
        return new JobBuilder("SCHEMA_LOADER", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(tableNameFTStep)
                .next(standardSelectionFTStep)
                .next(ftQueryBuildingStep)
                .next(bISyncFt)
                .next(tableNameAABillStep)
                .next(standardSelectionAABillStep)
                .next(aaQueryBuildingStep)
                .next(bISyncAaBill)
                .next(tableNameCustomerStep)
                .next(standardSelectionCustomerStep)
                .next(cusQueryBuildingStep)
                .next(bISyncCus)
                .build();
    }

    /**
     * @return le job n'extrayant que CUSTOMER ; réclamé par le qualificateur
     *         {@code customer} depuis le contrôleur et le scheduler
     */
    @Bean(name = "customer")
    public Job customerJob(Flow customerFlow, JobRepository jobRepository) {
        return buildSingleFlowJob("CUSTOMER.JOB", customerFlow, jobRepository);
    }

    /** @return le job n'extrayant que AA.BILL.DETAILS */
    @Bean(name = "billDetails")
    public Job billDetailsJob(Flow aaBillFlow, JobRepository jobRepository) {
        return buildSingleFlowJob("AA.BILL.DETAILS.JOB", aaBillFlow, jobRepository);
    }

    /** @return le job n'extrayant que FUNDS.TRANSFER$HIS */
    @Bean(name = "ftHis")
    public Job ftJob(Flow ftFlow, JobRepository jobRepository) {
        return buildSingleFlowJob("FT.HIS.JOB", ftFlow, jobRepository);
    }

    /**
     * Job n'extrayant qu'une seule table.
     *
     * <p>L'incrémenteur garantit des paramètres distincts à chaque lancement,
     * sans quoi Spring Batch refuserait de rejouer une instance déjà terminée.
     *
     * @param name nom du job, référencé par les appelants REST et le scheduler
     * @param flow enchaînement à exécuter
     * @return le job construit
     */
    private Job buildSingleFlowJob(String name, Flow flow, JobRepository jobRepository) {
        return new JobBuilder(name, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(flow)
                .end()
                .build();
    }

    /**
     * @return le décideur d'aiguillage entre branches XML et BLOB
     */
    @Bean
    public ExecutionFlowDecider executionFlowDecider() {
        return new ExecutionFlowDecider();
    }
}