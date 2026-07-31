package com.t24extractor.commons;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Constantes partagées : colonnes techniques, noms de tables, identifiants de
 * steps et requêtes SQL des dépôts.
 *
 * <p>Plusieurs de ces valeurs sont des invariants à portée large : les colonnes
 * techniques figurent dans les DDL générés et dans les vues BI, et les clés de
 * contexte forment le contrat entre étapes du job. Les modifier a des effets à
 * distance, sans erreur de compilation.
 */
public class Constants {
    private Constants() {
        /* This utility class should not be instantiated */
    }

    /**
     * Colonnes techniques dont la présence est obligatoire dans toute table BI.
     *
     * <ul>
     *   <li>{@code _id} — clé unique issue du RECID T24, renommée pour respecter
     *       la convention MongoDB où la clé primaire s'appelle {@code _id} ;</li>
     *   <li>{@code ORIGINAL_ID} — dérivée de {@code _id}, elle regroupe les
     *       versions d'un même enregistrement entre les tables live et
     *       historisées ;</li>
     *   <li>{@code INSERT_DATE} — horodatage posé à l'insertion en SQL Server.</li>
     * </ul>
     */
    public static final String ORIGINAL_ID = "ORIGINAL_ID";
    public static final String INSERT_DATE_COLUMN = "INSERT_DATE";
    protected static final Set<String> mandatoryColumns = new HashSet<>(Arrays.asList("_id", ORIGINAL_ID, INSERT_DATE_COLUMN));

    public static final String VIEW_PREFIX = "V_";

    public static final String ID = "_id";

    public static final int CHUNK_SIZE = 700;

    public static final int BLOB_CHUNK_SIZE = 200;
    public static final int BLOB_FETCH_SIZE = 200;
    public static final String INCREMENTAL = "Incremental";
    public static final String FULL = "Full";
    public static final String BATCH_CONFIG = "BATCH_CONFIG";
    public static final String STATS = "stats";
    public static final String SCHEMA_SOURCE = "SCHEMA_SOURCE";
    public static final String SCHEMA_SOURCE_AUDIT = "SCHEMA_SOURCE_AUDIT";
    public static final String T24_METADATA = "T24_Metadata";
    public static final String RAW_PREFIX = "RAW_";
    public static final String SCHEDULED_OPS = "SCHEDULED_OPS";

    public static final String COB_STOPPED="STOP";
    public static final String COB_RUNNING="COB_RUNNING";
    public static final String COB_RECID="COB";

    public static final String COUNT_ALL = "countAll";
    public static final String COUNT_CHUNK = "countChunk";
    public static final String FAILED_RETRY_COUNT = "FAILED_RETRY_COUNT";


    public static final String DAY_TO_EXTRACT = "DAY_TO_EXTRACT";
    public static final String EXTRACT = "EXTRACT";
    public static final String EXTRACTED = "EXTRACTED";
    public static final String COMPLETED_STATUS = "COMPLETED";
    public static final String FAILED_STATUS = "FAILED";
    public static final String RECID_COLUMN = "RECID";
    public static final String SERVICE_COLUMN = "SERVICE_CONTROL";
    public static final String SERVICE_TABLE = "V_F_TSA_SERVICE";
    public static final String STEP_COB_STATUS = "COB_STATUS";
    public static final String PROCESSED_ROWS = "PROCESSED_ROWS";
    public static final String MULTI_VALUE = "StringMAX";
    public static final String LOCAL_REF = "Local_Ref";
    public static final String SYS_SINGLE = "sys.single";
    public static final String VARCHAR_8000 = "VARCHAR(8000)";
    public static final String VARCHAR_MAX = "VARCHAR(MAX)";
    public static final String SINGLE = "single";
    public static final String S = "S";
    public static final String M = "M";
    public static final String USR = "usr";
    public static final String NEW_TABLE = "NEW_TABLE";
    public static final String MAX = "MAX";
    public static final String STANDARD_SELECTION_DICTIONARY = "Standard_Selection_Dict";
    public static final String BI_STANDARD_SELECTION = "sysFieldOds";
    public static final String ORACLE_TABLE_NAME = "Table_Name";
    public static final String COLONNE_REF = "colonne_ref";
    public static final String LAST_DATE = "last_date";

    public static final String MAX_VALUE = "MAX_VALUE";
    public static final String BI_SYNC = "BI_SYNC";
    public static final String BASIC_NORMALIZATION = "BASIC_NORMALIZATION";
    public static final String QUERY_BUILDER = "QUERY_BUILDER";
    public static final String T24_SELECTION_NAME_PARAMETER = "T24_Selection_Name";
    public static final String T24_DATE_PREFIX = "T24Date";
    public static final int INSERT_CODE = 0;
    public static final int UPDATE_CODE = 1;
    public static final int RETRY_COUNT0 = 0;
    public static final int RETRY_COUNT1 = 1;
    public static final int RETRY_COUNT2 = 2;
    public static final int RETRY_COUNT3 = 3;
    public static final String LAST_EXTRACTION = "LAST_EXTRACTION";
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ENGLISH);

    public static final String ID_TYPE= "NVARCHAR(255) NOT NULL";
    public static final String ORIGINAL_ID_TYPE = "NVARCHAR(250) not null";

    public static final String RECID = "TN0010001";

    public static final String RUN_ID = "run.id";


    public static final int CHUNKS_NUMBER = 700;
    public static final int FETCH_SIZE = 1000;
    public static final String FUNDS_TRANSFER_HIS = "FUNDS.TRANSFER$HIS";
    public static final String FBNK_CUSTOMER = "FBNK_CUSTOMER";
    public static final String FBNK_BILL_DETAILS = "FBNK_AA_BILL_DETAILS";
    public static final String CUSTOMER = "CUSTOMER";
    public static final String JSON_NORMALIZATION = "JSON_NORMALIZATION";
    public static final String QUERY_BUILDING_STEP = "QueryBuildingStep";
    public static final String AA_BILL_DETAILS = "AA.BILL.DETAILS";

    public static final String INITIAL_DATE = "00000000";

    /* =====================================================================
     *
     *  REQUÊTES SQL
     *
     *  Toutes les requêtes de l'application sont regroupées ici. Celles dont
     *  le nom se termine par _TPL sont des gabarits destinés à
     *  String.format ; les autres sont utilisables telles quelles.
     *
     *  Les requêtes marquées « Oracle » visent la source T24 (lecture seule),
     *  les autres la base BI (SQL Server).
     *
     * ===================================================================== */

    // ── Lecture des données T24 (Oracle) ─────────────────────────────────

    /** Lecture d'une table T24 stockée en XML. Gabarit : table Oracle. */
    public static final String SELECT_XML_RECORDS_TPL = "SELECT  RECID , XMLRECORD FROM %s";

    /**
     * Répartition des lignes entre workers par hachage du ROWID.
     * Paramètres : nombre de workers, index de la partition.
     */
    public static final String PARTITION_BY_ROWID = "WHERE MOD(ORA_HASH(ROWID), ?) = ?";

    /** Chargement « Incremental-specific » : mois courant et mois précédent. */
    public static final String WHERE_RECID_CURRENT_AND_PREVIOUS_MONTH =
            " WHERE RECID like CONCAT('%-', to_char(sysdate, 'yyyymm'))" +
                    " OR RECID like CONCAT('%-', to_char(sysdate, 'yyyymm')-1)";

    /** Restriction d'une vue BLOB à une plage de lignes. Gabarit : vue, borne basse, borne haute. */
    public static final String SELECT_VIEW_ROW_RANGE_TPL =
            "SELECT * FROM ( %s ) WHERE rowNumber >= %d AND rowNumber <= %d";

    /** Vue BLOB : préfixe des colonnes sélectionnées. */
    public static final String SELECT_BLOB_VIEW_PREFIX = "SELECT RECID as FIELD_ID, ";

    /** Vue BLOB : suffixe portant le numéro de ligne. Gabarit : table Oracle. */
    public static final String SELECT_BLOB_VIEW_SUFFIX_TPL = ", ROWNUM rowNumber FROM V_%s t";

    // ── Comptages ────────────────────────────────────────────────────────

    /** Comptage exact, coûteux sur les grosses tables. Gabarit : table. */
    public static final String COUNT_ALL_ROWS_TPL = "SELECT COUNT(1) FROM %s";

    /**
     * Oracle : comptage par les statistiques du dictionnaire, avec repli sur
     * les statistiques de table. Paramètres : table, table.
     */
    public static final String ORACLE_COUNT_FROM_STATS = """
            SELECT NVL(
                (SELECT SUM(num_rows) FROM user_tab_partitions WHERE table_name = UPPER(?)),
                (SELECT num_rows FROM user_tables WHERE table_name = UPPER(?))
            ) AS total_rows
            FROM dual
            """;

    /** Oracle : comptage plafonné à cinq lignes. Gabarit : table. */
    public static final String ORACLE_COUNT_TOP_FIVE_TPL =
            "SELECT COUNT(*) FROM (SELECT 1 FROM %s FETCH FIRST 5 ROWS ONLY)";

    /** SQL Server : comptage par les statistiques de partition. Gabarit : table. */
    public static final String SQLSERVER_COUNT_FROM_STATS_TPL = """
            SELECT
               Total_Rows= SUM(st.row_count)
            FROM
               sys.dm_db_partition_stats st
            WHERE
                object_name(object_id) = '%s'""";

    /** SQL Server : comptage plafonné à cinq lignes. Gabarit : table. */
    public static final String SQLSERVER_COUNT_TOP_FIVE_TPL = "SELECT TOP 5 count(*) FROM dbo.%s";

    // ── Structure des tables BI (SQL Server) ─────────────────────────────

    /** Test d'existence conditionnant l'instruction suivante. Gabarit : table. */
    public static final String IF_EXISTS_TABLE_TPL =
            "IF EXISTS (SELECT * FROM sys.tables WHERE name ='%s')";

    /** Colonnes métier d'une table BI, hors colonnes techniques. Gabarit : table, colonne INSERT_DATE. */
    public static final String SELECT_DEST_COLUMNS_TPL =
            "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS\n"
                    + "WHERE TABLE_NAME = N'%s' AND COLUMN_NAME NOT IN ('_id', 'ORIGINAL_ID', '%s')";

    /** Présence d'une colonne dans une table. Paramètres : table, colonne. */
    public static final String COUNT_COLUMN_IN_TABLE =
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE TABLE_NAME = ? AND COLUMN_NAME = ?";

    /** Valeur par défaut de INSERT_DATE : horodatage compacté en BIGINT. */
    public static final String INSERT_DATE_DEFAULT =
            "BIGINT default replace(replace(replace(CONVERT([varchar](19),sysdatetime(),20),'-',''),':',''),' ','')";

    /**
     * Création d'une table BI avec ses colonnes métier.
     *
     * <p>Gabarit : nom de table (répété), bloc des colonnes métier — vide, ou
     * précédé d'une virgule.
     */
    public static final String CREATE_DEST_TABLE_TPL =
            "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name='%1$s') "
                    + "CREATE TABLE [%1$s]("
                    + "[" + ID + "]" + ID_TYPE + ", \n"
                    + "[" + ORIGINAL_ID + "]" + ORIGINAL_ID_TYPE + " ,\n"
                    + "[" + INSERT_DATE_COLUMN + "] " + INSERT_DATE_DEFAULT + " \n"
                    + "%2$s, \n"
                    + "INDEX ins_date_idx NONCLUSTERED ([" + INSERT_DATE_COLUMN + "]) \n"
                    + ")";

    /** Création d'une table BI réduite aux colonnes techniques. Gabarit : nom de table (répété). */
    public static final String CREATE_DEFAULT_DEST_TABLE_TPL =
            "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name='%1$s') "
                    + "CREATE TABLE [%1$s]("
                    + "[" + ID + "]" + ID_TYPE + ", \n"
                    + "[" + ORIGINAL_ID + "]" + ORIGINAL_ID_TYPE + " ,\n"
                    + "[" + INSERT_DATE_COLUMN + "] " + INSERT_DATE_DEFAULT + ", \n"
                    + "INDEX ins_date_idx NONCLUSTERED ([" + INSERT_DATE_COLUMN + "]) \n"
                    + ")";

    /** Création de la table de dépôt brut. Gabarit : table déjà préfixée (répétée). */
    public static final String CREATE_RAW_TABLE_TPL =
            "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name='%1$s') "
                    + "CREATE TABLE [%1$s](raw_id   NVARCHAR(255),\nraw_data VARCHAR(MAX))";

    /** Ajout de colonne. Gabarit : table, colonne échappée, type SQL. */
    public static final String ALTER_TABLE_ADD_COLUMN_TPL = "ALTER TABLE %s\nADD %s %s";

    /** Suppression de colonne. Gabarit : table, colonne. */
    public static final String ALTER_TABLE_DROP_COLUMN_TPL = "\n ALTER TABLE %s \n DROP COLUMN %s";

    /** Vidage d'une table. Gabarit : table échappée. */
    public static final String TRUNCATE_TABLE_TPL = "TRUNCATE TABLE %s";

    /** Suppression d'une table. Gabarit : table échappée. */
    public static final String DROP_TABLE_TPL = "DROP TABLE %s";

    // ── Index ────────────────────────────────────────────────────────────

    /** Recherche d'un index par nom. Gabarit : nom d'index, table. */
    public static final String SELECT_INDEX_TPL = """
            SELECT name
            FROM sys.indexes
            WHERE name='%s' AND object_id = OBJECT_ID('dbo.%s')""";

    /** Création d'un index. Gabarit : colonne échappée (nom de l'index), table échappée. */
    public static final String CREATE_INDEX_TPL = "CREATE NONCLUSTERED INDEX %1$s ON %2$s(%1$s)";

    /** Suppression d'un index. Gabarit : index échappé. */
    public static final String DROP_INDEX_TPL = "DROP INDEX %s";

    // ── Alimentation des tables BI ───────────────────────────────────────

    /** INSERT paramétré. Gabarit : table échappée, colonnes, placeholders. */
    public static final String INSERT_INTO_TPL = "INSERT INTO %s(%s) VALUES (%s);";

    /** Rapprochement d'une table avec sa version de travail. Gabarit : table, table, clé, clé, affectations. */
    public static final String MERGE_INTO_TPL =
            "MERGE INTO %s trg USING (SELECT * FROM %s ) src ON  (src.%s= trg.%s ) %s ); ";

    /** Enregistrement d'un champ multivalué, sans doublon. Gabarit : table BI, champ, indicateur local_ref. */
    public static final String INSERT_METADATA_IF_ABSENT_TPL =
            "IF NOT EXISTS(SELECT * from " + T24_METADATA + " Where [Table_Name]='%1$s' and MultiValueFields='%2$s')"
                    + "BEGIN\n"
                    + "         INSERT INTO " + T24_METADATA + " VALUES ('%1$s','%2$s','%3$s')\n"
                    + "         END";

    /** UPDATE composé dynamiquement. Gabarit : table, affectations, condition. */
    public static final String UPDATE_TABLE_TPL = "UPDATE %s\n SET %s\n WHERE %s";

    /** Correction du type de stockage détecté via TAFJ. Gabarit : type, table Oracle. */
    public static final String UPDATE_BATCH_CONFIG_TYPE_TPL =
            "UPDATE batch_config SET \"Type\" = '%s' WHERE \"TableName\" = '%s'";

    // ── Normalisation JSON : tables satellites TABLE#CHAMP ───────────────

    /** Création d'un satellite multivalué. Gabarit : table, champ, table, champ, type de clé, champ. */
    public static final String CREATE_SATELLITE_TABLE_TPL =
            "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name='%1$s#%2$s') "
                    + "CREATE TABLE [%1$s#%2$s] ([" + ID + "]%3$s, \n"
                    + "[%2$s] nvarchar(355) ,\n"
                    + "[MV_SEQUENCE] int ,\n"
                    + "[SEQUENCE] int \n"
                    + ")";

    /** Création d'un satellite LOCAL.REF, sans multivaleur. Gabarit : table, champ, type de clé. */
    public static final String CREATE_LOCAL_REF_SATELLITE_TPL =
            "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name='%1$s#%2$s') "
                    + "CREATE TABLE [%1$s#%2$s] ([" + ID + "]%3$s, \n"
                    + "[%2$s] nvarchar(355) ,\n"
                    + "[SEQUENCE] int \n"
                    + ")";

    /** Purge d'un satellite avant réalimentation — garantit l'idempotence. Gabarit : table, champ. */
    public static final String PURGE_SATELLITE_TPL =
            "IF EXISTS (SELECT * FROM sys.tables WHERE name='%1$s#%2$s') DELETE FROM [%1$s#%2$s]";

    /** Éclatement d'un champ multivalué. Gabarit : table, champ (répétés). */
    public static final String INSERT_SATELLITE_TPL = """
            INSERT INTO [%1$s#%2$s] (_id, %2$s, [MV_SEQUENCE], [SEQUENCE])
            SELECT _id, lda.%2$s, lda.[MV_SEQUENCE], lda.[SEQUENCE]
            FROM %1$s AS ld
            CROSS APPLY OPENJSON(ld.%2$s)
            WITH (
              %2$s nvarchar(255) N'strict $.%2$s',
              [MV_SEQUENCE] int N'$.mvsequence',
              [SEQUENCE] int N'$.sequence'
            ) AS lda""";

    /** Éclatement d'un champ LOCAL.REF. Gabarit : table, champ (répétés). */
    public static final String INSERT_LOCAL_REF_SATELLITE_TPL = """
            INSERT INTO [%1$s#%2$s] (_id, [%2$s], [SEQUENCE])
            SELECT _id, lf.%2$s, lf.[SEQUENCE]
            FROM %1$s AS LF_table
            CROSS APPLY OPENJSON(LF_table.%2$s)
            WITH (
            %2$s nvarchar(355) N'$.%2$s',
            [SEQUENCE] int N'$.sequence'
            ) AS lf""";

    /** Procédure de normalisation appelée sur la base BI. */
    public static final String JSON_NORMALIZATION_PROCEDURE = "basic_json_normalization";

    /** Schéma portant la procédure de normalisation. */
    public static final String DBO_SCHEMA = "dbo";

    // ── Référentiel des colonnes (SCHEMA_SOURCE) ─────────────────────────
    //
    //    Requêtes nommées des dépôts Spring Data JDBC : les paramètres y sont
    //    désignés par leur nom (:tableName), et non par des points d'interrogation.

    /** Colonnes actives d'une table. Paramètre nommé : tableName. */
    public static final String SCHEMA_ENABLED = "SELECT id, table_name, column_name, datatype, _enabled, indexed, ssTag, single_mult, local_ref FROM "+SCHEMA_SOURCE+" WHERE _enabled = 1 and table_name=:tableName";

    /** Toutes les colonnes déclarées d'une table. Paramètre nommé : tableName. */
    public static final String SCHEMA_TABLE = "SELECT id, table_name, column_name, datatype, _enabled, indexed, ssTag, single_mult, local_ref FROM "+SCHEMA_SOURCE+" WHERE table_name = :tableName ";

    /** Une colonne précise. Paramètres nommés : tableName, columnName. */
    public static final String SCHEMA_COLUMN_TABLE = "SELECT id, table_name,  column_name, datatype, _enabled, indexed, ssTag, single_mult, local_ref FROM "+SCHEMA_SOURCE+" WHERE table_name= :tableName AND column_name= :columnName";

    /** Nombre de colonnes déclarées ; zéro signale une table jamais chargée. */
    public static final String COUNT_ROWS_TABLE_SCHEMA_SOURCE = "SELECT count(id) FROM "+SCHEMA_SOURCE+" WHERE table_name= :tableName";

    /** Nombre de colonnes actives. Paramètre nommé : tableName. */
    public static final String COUNT_ENABLED_ROWS_TABLE_SCHEMA_SOURCE = "SELECT count(id) FROM "+SCHEMA_SOURCE+" WHERE table_name= :tableName AND _enabled = 1";

    /**
     * Changements de schéma en attente. Paramètre nommé : tableName.
     *
     * <p>Les indicateurs sont convertis en entiers : c'est l'écart entre l'ancien
     * et le nouveau qui décide de l'ALTER TABLE à émettre.
     */
    public static final String SCHEMA_AUDIT_TABLE = "SELECT " +
            "id, sourceId, sourceTable_name, sourceColumn_name, sourceDatatype, cast (newSource_enabled as int) as newSource_enabled, cast(oldSource_enabled as int) as oldSource_enabled, cast (newSourceIndexed as int) as newSourceIndexed, cast (oldSourceIndexed as int) as oldSourceIndexed FROM "+SCHEMA_SOURCE_AUDIT+" WHERE sourceTable_name = :tableName";

    /** Activation d'une colonne. Paramètres nommés : enabled, tableName, columnName. */
    public static final String UPDATE_SCHEMA_SOURCE_ENABLED = "UPDATE " + SCHEMA_SOURCE
            + " SET _enabled = :enabled WHERE table_name = :tableName AND column_name = :columnName";

    /** Opérations de maintenance en attente pour une table. Paramètre nommé : table. */
    public static final String SCHEDULED_OPS_QUERY = "SELECT table_name, column_name, op FROM " + SCHEDULED_OPS + " WHERE table_name = :table";

    // ── Sans appelant à ce jour ──────────────────────────────────────────
    //
    //    Ces deux préfixes ne sont référencés nulle part dans le code actuel.
    //    Conservés en attendant confirmation qu'ils sont bien obsolètes.

    /** Préfixe d'un comptage sur RECID ; à compléter par le nom de table. */
    public static final String COUNT_QUERY = "SELECT COUNT (RECID) FROM ";

    /** Préfixe d'une lecture XML ; à compléter par le nom de table. */
    public static final String BASE_QUERY = "SELECT RECID, XMLRECORD FROM ";

    // ── Configuration des extractions (BATCH_CONFIG) ─────────────────────

    /** Configuration complète de chaque extraction, pour l'IHM. */
    public static final String SELECT_BATCH_CONFIGS = """
            SELECT id, TableName, T24SelectionName, colonne_ref, chargement,
                   last_date, min_date, enabled, scheduler, scheduler_time
            FROM BATCH_CONFIG
            ORDER BY id
            """;

    /** Extractions planifiées et actives, lues par le scheduler. */
    public static final String SELECT_SCHEDULED_BATCHES = """
            SELECT id, scheduler_time
            FROM BATCH_CONFIG
            WHERE enabled = 1
              AND scheduler = 1
              AND scheduler_time IS NOT NULL
              AND LTRIM(RTRIM(scheduler_time)) <> ''
            """;

    /** Heure de planification enregistrée. Paramètre : identifiant. */
    public static final String SELECT_SCHEDULER_TIME =
            "SELECT scheduler_time FROM BATCH_CONFIG WHERE id = ?";

    /** Bascule de la planification. Paramètres : indicateur, identifiant. */
    public static final String UPDATE_SCHEDULER =
            "UPDATE BATCH_CONFIG SET scheduler = ? WHERE id = ?";

    /** Bascule et heure de planification. Paramètres : indicateur, heure, identifiant. */
    public static final String UPDATE_SCHEDULER_AND_TIME =
            "UPDATE BATCH_CONFIG SET scheduler = ?, scheduler_time = ? WHERE id = ?";

    /** Activation d'une extraction. Paramètres : indicateur, identifiant. */
    public static final String UPDATE_BATCH_ENABLED =
            "UPDATE BATCH_CONFIG SET enabled = ? WHERE id = ?";

}
