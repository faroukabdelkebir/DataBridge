package com.t24extractor.bi_sync.tasklet;

import com.t24extractor.bi_sync.ops.SqlOps;
import com.t24extractor.bi_sync.ops.SqlServerOps;
import com.t24extractor.model.MetaDataTable;
import com.t24extractor.model.ScheduledOps;
import com.t24extractor.model.SchemaSource;
import com.t24extractor.model.SchemaSourceAudit;
import com.t24extractor.repository.bi_repos.MetaDataRepository;
import com.t24extractor.repository.bi_repos.ScheduledOpsRepository;
import com.t24extractor.repository.bi_repos.SchemaSourceAuditRepository;
import com.t24extractor.repository.bi_repos.SchemaSourceRepository;
import com.t24extractor.utils.Renamer;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.springframework.batch.core.ExitStatus;

import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.t24extractor.commons.Constants.*;

/**
 * Aligne la table BI sur le schéma attendu avant toute écriture de données.
 *
 * <p>Étape charnière du job : elle garantit que la table cible existe, qu'elle
 * porte exactement les colonnes qui seront insérées, et publie les artefacts
 * dont dépend le reste du traitement.
 *
 * <h2>Déroulement</h2>
 * <ol>
 *   <li>création de la table cible si elle n'existe pas ;</li>
 *   <li>table inconnue de SCHEMA_SOURCE — le job s'arrête sur le statut
 *       {@code NEW_TABLE}, faute de schéma exploitable ;</li>
 *   <li>application des changements en attente dans SCHEMA_SOURCE_AUDIT
 *       (ALTER TABLE, index, métadonnées multivaluées), puis purge de l'audit ;</li>
 *   <li>publication du dictionnaire nettoyé, de la liste des colonnes et de
 *       l'INSERT.</li>
 * </ol>
 *
 * <h2>Cohérence des artefacts</h2>
 * {@code sysFieldOds}, {@code COLUMNS} et {@code SQL_QUERY} sont tous dérivés du
 * même ensemble de colonnes, dans un {@code LinkedHashSet} partagé. C'est ce qui
 * garantit que l'ordre des colonnes et celui des paramètres de l'INSERT ne
 * peuvent pas diverger.
 *
 * @see com.t24extractor.model.SchemaSourceAudit
 * @see com.t24extractor.bi_sync.ops.SqlServerOps
 */
@Component
@Slf4j
public class BISyncTasklet implements AbstractBISyncTasklet, Tasklet {

    private final SchemaSourceRepository schemaSourceRepository;

    private final SchemaSourceAuditRepository schemaSourceAuditRepository;

    private final MetaDataRepository metaDataRepository;

    private final ScheduledOpsRepository scheduledOpsRepository;


    private final SqlOps sqlOps;

    private final Renamer renamer;


    private final JdbcTemplate jdbcTemplate;

    public BISyncTasklet(
            SchemaSourceRepository schemaSourceRepository,
            SchemaSourceAuditRepository schemaSourceAuditRepository,
            MetaDataRepository metaDataRepository,
            ScheduledOpsRepository scheduledOpsRepository,
            @Qualifier("jdbcBI") JdbcTemplate jdbcTemplate) {
        this.schemaSourceRepository = schemaSourceRepository;
        this.schemaSourceAuditRepository = schemaSourceAuditRepository;
        this.metaDataRepository = metaDataRepository;
        this.scheduledOpsRepository = scheduledOpsRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.sqlOps = new SqlServerOps();
        this.renamer = new Renamer();
    }

    @Setter
    JSONObject ss;

    @Setter
    String t24TableName;

    @Setter
    String colonneRef;

    /**
     * {@inheritDoc}
     *
     * @return toujours {@link RepeatStatus#FINISHED} ; l'arrêt sur table inconnue
     *         passe par le statut de sortie {@code NEW_TABLE}
     */
    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {

        String biTable = renamer.biRenamer(this.t24TableName);

        //Creation des colonnes par defaut _id,ORIGINAL_ID et INSERT_DATE
        log.info("Création des colonnes par défaut pour la table {}", biTable);
        sqlOps.createDestTable(jdbcTemplate, biTable, new HashSet<>(schemaSourceRepository.findSchemaSourceByTableNameAndEnabled(this.t24TableName, true)));

        //Vérification si la table en cours de chargement avait ses colonnes traitées dans la table SCHEMA_SOURCE ou pas.
        if (!statusSchemaSource(biTable)) {

            //Alimentation de la table SCHEMA_SOURCE avec les données la table
            log.info("Alimenter du schéma dans la table SchemaSource");
            loadSchemaSource(biTable, this.ss);

            stepContribution.setExitStatus(new ExitStatus(NEW_TABLE));
            stepContribution.getStepExecution().getJobExecution().setExitStatus(new ExitStatus(NEW_TABLE));
            return RepeatStatus.FINISHED;
        }

        processSchemaChanges(biTable);
        populateExecutionContext(stepContribution, biTable);
        return RepeatStatus.FINISHED;
    }

    private void processSchemaChanges(String biTable) {
        List<SchemaSourceAudit> changesList = schemaSourceAuditRepository.findAllBySourceTableName(biTable);
        if (changesList.isEmpty()) {
            return;
        }

        for (SchemaSourceAudit change : changesList) {
            int diff = change.newSourceEnabled() - change.oldSourceEnabled();
            if (diff == 1 && change.dataType().contains(MAX)) {
                SchemaSource sch = schemaSourceRepository.findAllByTableNameAndColumnName(change.sourceTableName(), change.ColumnName())
                        .orElseThrow(() -> new RuntimeException("La colonne saisie n'existe pas dans la table sourceSchema!"));
                List<MetaDataTable> metaDataTableList = new ArrayList<>();
                metaDataTableList.add(new MetaDataTable(biTable, change.ColumnName(), sch.isLocalRef()));
                sqlOps.save(jdbcTemplate, metaDataTableList);
            }
            if (diff == -1 && change.dataType().contains(MAX)) {
                SchemaSource sch = schemaSourceRepository.findAllByTableNameAndColumnName(change.sourceTableName(), change.ColumnName())
                        .orElseThrow(() -> new RuntimeException("La colonne saisie n'existe pas dans la table sourceSchema!"));
                metaDataRepository.delete(new MetaDataTable(biTable, change.ColumnName(), sch.isLocalRef()));
            }

            updateColumn(biTable, diff, change.ColumnName(), change.dataType());
            int diffIndex = change.newSourceIndexed() - change.oldSourceIndexed();
            updateIndex(biTable, diffIndex, change.ColumnName());
        }
        schemaSourceAuditRepository.deleteAll(changesList);
    }

    /**
     * Alimente le contexte d'exécution avec des artefacts COHÉRENTS entre eux :
     * le mapping XML (BI_STANDARD_SELECTION), la liste des colonnes (COLUMNS)
     * et l'INSERT (SQL_QUERY) sont désormais tous dérivés du MÊME ensemble.
     *
     * Corrige deux défauts de l'ancienne version :
     *  1. COLUMNS / SQL_QUERY étaient construits depuis les colonnes
     *     « _enabled = 1 » brutes, alors que le mapping XML était filtré par
     *     le STANDARD.SELECTION → une colonne active mais absente du
     *     STANDARD.SELECTION (ex. BILL_REFERENCE) entrait dans l'INSERT sans
     *     exister dans la table cible ni être alimentée par le processeur,
     *     faisant échouer le writer (« Unable to retrieve data from the
     *     source » en mode useBulkCopyForBatchInsert).
     *  2. Deux HashSet distincts étaient créés pour COLUMNS et SQL_QUERY :
     *     l'alignement ordre des colonnes ↔ ordre des placeholders ne tenait
     *     qu'à la stabilité d'itération de HashSet. Un unique LinkedHashSet
     *     est maintenant partagé par les deux constructions.
     */
    private void populateExecutionContext(StepContribution stepContribution, String biTable) {

        // 1. Nettoyage du STANDARD.SELECTION — la référence unique du pipeline.
        JSONObject cleanedSS = ssMapClean(biTable, this.ss);

        // 2. Colonnes actives, filtrées par le même critère que le mapping XML.
        Set<SchemaSource> insertable = schemaSourceRepository
                .findSchemaSourceByTableNameAndEnabled(biTable, true)
                .stream()
                .filter(column -> {
                    boolean mapped = cleanedSS.has(column.getColumnName());
                    if (!mapped) {
                        log.warn("Colonne '{}' active dans SCHEMA_SOURCE mais absente du "
                                        + "STANDARD.SELECTION : exclue de l'INSERT pour la table '{}'",
                                column.getColumnName(), biTable);
                    }
                    return mapped;
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 3. Auto-réparation : toute colonne insérable encore absente de la
        //    table cible est matérialisée (couvre les activations dont l'audit
        //    a été consommé sans que l'ALTER TABLE ait réellement abouti).
        ensureDestColumns(biTable, insertable);

        stepContribution.getStepExecution().getJobExecution().getExecutionContext()
                .put(BI_STANDARD_SELECTION, cleanedSS.toString());
        stepContribution.getStepExecution().getJobExecution().getExecutionContext()
                .put("COLUMNS", sqlOps.fieldsToInsert(insertable));
        stepContribution.getStepExecution().getJobExecution().getExecutionContext()
                .put("SQL_QUERY", sqlOps.insertConstruction(biTable, insertable));
    }

    /**
     * Garantit que chaque colonne destinée à l'INSERT existe physiquement
     * dans la table BI — le pipeline redevient auto-cohérent même si la
     * table et SCHEMA_SOURCE ont divergé (run interrompu, audit purgé…).
     */
    private void ensureDestColumns(String biTable, Set<SchemaSource> insertable) {
        Set<String> existing = sqlOps.destColumns(jdbcTemplate, biTable);
        for (SchemaSource column : insertable) {
            if (!existing.contains(column.getColumnName())) {
                log.info("Colonne '{}' manquante dans la table '{}' : création automatique ({})",
                        column.getColumnName(), biTable, column.getDataType());
                sqlOps.addCol(jdbcTemplate, biTable, column.getColumnName(), column.getDataType());
            }
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean statusSchemaSource(String tableName) {
        return schemaSourceRepository.countByTableName(tableName) != 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadMetaDataTable(String tableName, @NotNull List<SchemaSource> schemaSourceList) {
        List<MetaDataTable> metaList = new ArrayList<>();
        for (SchemaSource row : schemaSourceList) {
            if (row.isLocalRef()) metaList.add(new MetaDataTable(tableName, row.getColumnName(), true));
            if (!row.isLocalRef()) metaList.add(new MetaDataTable(tableName, row.getColumnName(), false));
        }
        sqlOps.save(jdbcTemplate, metaList);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void checkSchemaUpdate(String tableName) {
        schemaSourceAuditRepository.findAllBySourceTableName(tableName);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sans effet : les opérations différées sont enregistrées directement par
     * {@link #updateIndex(String, int, String)}.
     */
    @Override
    public void scheduleOp(String tableName) {
        //
    }

    /**
     * {@inheritDoc}
     *
     * <p>Au-delà de dix millions de lignes, la création est enregistrée dans
     * SCHEDULED_OPS au lieu d'être exécutée : un index créé en ligne
     * verrouillerait la table pendant toute l'extraction.
     */
    @Override
    public void updateIndex(String tableName, int diff, String column) {
        if (diff == 1) {
            if (countTable(jdbcTemplate, tableName) > 10000000) {
                scheduledOpsRepository.save(new ScheduledOps(null, sqlOps.escapeColumn(tableName), sqlOps.escapeColumn(column), "I"));
            } else {
                sqlOps.createIndex(jdbcTemplate, tableName, column);
            }
        }
        if (diff == -1) sqlOps.dropIndex(jdbcTemplate, column);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateColumn(String table, int diff, String column, String dataType) {
        if (diff == 1) {
            sqlOps.addCol(jdbcTemplate, table, column, dataType);

        }
        if (diff == -1) sqlOps.dropCol(jdbcTemplate, table, column);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sans effet : SCHEMA_SOURCE est alimentée en amont par l'IHM
     * d'administration, pas par le batch. Le point d'extension est conservé pour
     * un éventuel auto-enregistrement des nouvelles tables.
     */
    @Override
    public void loadSchemaSource(String tableName, @NotNull JSONObject ss) {

        // alimenting table in mysqlServer ( the data already exist )
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JSONObject ssMapClean(String tableName, JSONObject ssMap) {
        List<SchemaSource> list = schemaSourceRepository.findAll(tableName);
        JSONObject newSSMap = new JSONObject();
        for (SchemaSource schemaSource : list) {
            String columnName = schemaSource.getColumnName();
            if (ssMap.has(columnName) && !ssMap.isNull(columnName)) {
                newSSMap.put(columnName, ssMap.get(columnName));
            } else {
                log.warn("Colonne '{}' absente du STANDARD.SELECTION pour la table '{}', ignorée pendant BI_SYNC", columnName, tableName);
            }
        }
        ssMap.clear();
        return newSSMap;
    }

    /**
     * @param jdbcTemplate accès à la base BI
     * @param tableName    table à compter
     * @return le nombre de lignes, d'après les statistiques SQL Server
     */
    public Long countTable(JdbcTemplate jdbcTemplate, String tableName) {
        return sqlOps.countAll(jdbcTemplate, tableName);
    }


}