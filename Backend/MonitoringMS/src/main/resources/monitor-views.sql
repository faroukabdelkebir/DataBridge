-- ============================================================
--  T24 Monitor — Vues SQL Server optimisées pour le monitoring
--  À exécuter une fois sur la base BI (T24_TEST)
-- ============================================================

-- ── Vue : dernière exécution de chaque job ──────────────────
GO
CREATE OR ALTER VIEW V_JOB_LAST_EXECUTION AS
SELECT
    ji.JOB_NAME,
    je.JOB_EXECUTION_ID,
    je.STATUS,
    je.EXIT_CODE,
    je.START_TIME,
    je.END_TIME,
    je.LAST_UPDATED,
    DATEDIFF(second, je.START_TIME, je.END_TIME)   AS DURATION_SEC,
    CASE WHEN je.END_TIME IS NULL THEN 1 ELSE 0 END AS IS_RUNNING
FROM BATCH_JOB_EXECUTION je
INNER JOIN BATCH_JOB_INSTANCE ji ON ji.JOB_INSTANCE_ID = je.JOB_INSTANCE_ID
WHERE je.JOB_EXECUTION_ID IN (
    SELECT MAX(je2.JOB_EXECUTION_ID)
    FROM BATCH_JOB_EXECUTION je2
    INNER JOIN BATCH_JOB_INSTANCE ji2 ON ji2.JOB_INSTANCE_ID = je2.JOB_INSTANCE_ID
    GROUP BY ji2.JOB_NAME
);
GO

-- ── Vue : métriques de step agrégées par exécution ──────────
CREATE OR ALTER VIEW V_JOB_STEP_AGGREGATES AS
SELECT
    je.JOB_EXECUTION_ID,
    ji.JOB_NAME,
    je.STATUS                                         AS JOB_STATUS,
    je.START_TIME,
    je.END_TIME,
    SUM(se.READ_COUNT)                                AS TOTAL_READ,
    SUM(se.WRITE_COUNT)                               AS TOTAL_WRITE,
    SUM(se.READ_SKIP_COUNT + se.WRITE_SKIP_COUNT
        + se.PROCESS_SKIP_COUNT)                      AS TOTAL_SKIP,
    SUM(se.ROLLBACK_COUNT)                            AS TOTAL_ROLLBACK,
    COUNT(se.STEP_EXECUTION_ID)                       AS STEP_COUNT,
    CASE
        WHEN DATEDIFF(second, je.START_TIME, je.END_TIME) > 0
             AND SUM(se.WRITE_COUNT) > 0
        THEN CAST(SUM(se.WRITE_COUNT) AS FLOAT)
             / DATEDIFF(second, je.START_TIME, je.END_TIME)
        ELSE NULL
    END                                               AS THROUGHPUT_PER_SEC
FROM BATCH_JOB_EXECUTION je
INNER JOIN BATCH_JOB_INSTANCE ji ON ji.JOB_INSTANCE_ID = je.JOB_INSTANCE_ID
LEFT  JOIN BATCH_STEP_EXECUTION se ON se.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID
GROUP BY je.JOB_EXECUTION_ID, ji.JOB_NAME, je.STATUS,
         je.START_TIME, je.END_TIME;
GO

-- ── Vue : tableau de bord quotidien ─────────────────────────
CREATE OR ALTER VIEW V_DAILY_DASHBOARD AS
SELECT
    CONVERT(varchar(8), je.CREATE_TIME, 112)            AS EXEC_DATE,
    ji.JOB_NAME,
    COUNT(*)                                             AS TOTAL_EXECUTIONS,
    SUM(CASE WHEN je.STATUS='COMPLETED' THEN 1 ELSE 0 END) AS COMPLETED,
    SUM(CASE WHEN je.STATUS='FAILED'    THEN 1 ELSE 0 END) AS FAILED,
    CAST(SUM(CASE WHEN je.STATUS='COMPLETED' THEN 1 ELSE 0 END) AS FLOAT)
        / NULLIF(COUNT(*), 0) * 100                     AS SUCCESS_RATE,
    AVG(DATEDIFF(second, je.START_TIME, je.END_TIME))   AS AVG_DURATION_SEC,
    MAX(DATEDIFF(second, je.START_TIME, je.END_TIME))   AS MAX_DURATION_SEC
FROM BATCH_JOB_EXECUTION je
INNER JOIN BATCH_JOB_INSTANCE ji ON ji.JOB_INSTANCE_ID = je.JOB_INSTANCE_ID
WHERE je.STATUS IN ('COMPLETED', 'FAILED')
GROUP BY CONVERT(varchar(8), je.CREATE_TIME, 112), ji.JOB_NAME;
GO

-- ── Vue : réconciliation par table (dernier run) ─────────────
CREATE OR ALTER VIEW V_RECONCILIATION_LATEST AS
SELECT
    s.mssql_table                                        AS TABLE_NAME,
    s.business_date                                      AS BUSINESS_DATE,
    s.oracle_count                                       AS ORACLE_COUNT,
    s.mongo_count                                        AS SQLSERVER_COUNT,
    s.oracle_count - s.mongo_count                       AS DELTA,
    CASE WHEN s.oracle_count > 0
         THEN CAST(s.mongo_count AS FLOAT) / s.oracle_count * 100.0
         ELSE 100.0
    END                                                  AS RECONCILIATION_RATE,
    CASE
        WHEN s.oracle_count = 0 THEN 'OK'
        WHEN CAST(s.mongo_count AS FLOAT) / s.oracle_count >= 0.95 THEN 'OK'
        WHEN CAST(s.mongo_count AS FLOAT) / s.oracle_count >= 0.80 THEN 'WARNING'
        ELSE 'KO'
    END                                                  AS STATUS
FROM stats s
INNER JOIN (
    SELECT mssql_table, MAX(insert_date) AS max_date
    FROM stats
    GROUP BY mssql_table
) latest ON s.mssql_table = latest.mssql_table
         AND s.insert_date = latest.max_date;
GO

-- ── Vue : évolution du schéma (derniers 30 jours) ────────────
CREATE OR ALTER VIEW V_SCHEMA_RECENT_CHANGES AS
SELECT
    sa.id,
    sa.sourceTable_name                              AS TABLE_NAME,
    sa.sourceColumn_name                             AS COLUMN_NAME,
    sa.sourceDatatype                                AS DATA_TYPE,
    CASE
        WHEN sa.oldSource_enabled = 0 AND sa.newSource_enabled = 1 THEN 'ACTIVATED'
        WHEN sa.oldSource_enabled = 1 AND sa.newSource_enabled = 0 THEN 'DEACTIVATED'
        WHEN sa.oldSourceIndexed  = 0 AND sa.newSourceIndexed  = 1 THEN 'INDEXED'
        WHEN sa.oldSourceIndexed  = 1 AND sa.newSourceIndexed  = 0 THEN 'UNINDEXED'
        ELSE 'MODIFIED'
    END                                              AS CHANGE_TYPE,
    sa.UpdatedBy,
    sa.UpdatedOn
FROM SCHEMA_SOURCE_AUDIT sa
WHERE sa.UpdatedOn >= CAST(FORMAT(DATEADD(day, -30, GETDATE()), 'yyyyMMddHHmmss') AS bigint);
GO

-- ── Index complémentaires ────────────────────────────────────
-- Accélère les requêtes de monitoring sur BATCH_JOB_EXECUTION

IF NOT EXISTS (SELECT * FROM sys.indexes
               WHERE name = 'IX_JOB_EXEC_STATUS_CREATE' AND object_id = OBJECT_ID('BATCH_JOB_EXECUTION'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_JOB_EXEC_STATUS_CREATE
        ON BATCH_JOB_EXECUTION (STATUS, CREATE_TIME DESC)
        INCLUDE (JOB_INSTANCE_ID, START_TIME, END_TIME, EXIT_CODE, LAST_UPDATED);
END
GO

IF NOT EXISTS (SELECT * FROM sys.indexes
               WHERE name = 'IX_STEP_JOB_EXEC' AND object_id = OBJECT_ID('BATCH_STEP_EXECUTION'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_STEP_JOB_EXEC
        ON BATCH_STEP_EXECUTION (JOB_EXECUTION_ID)
        INCLUDE (STEP_NAME, STATUS, READ_COUNT, WRITE_COUNT,
                 READ_SKIP_COUNT, WRITE_SKIP_COUNT, PROCESS_SKIP_COUNT,
                 ROLLBACK_COUNT, COMMIT_COUNT, START_TIME, END_TIME);
END
GO

IF NOT EXISTS (SELECT * FROM sys.indexes
               WHERE name = 'IX_STATS_TABLE_DATE' AND object_id = OBJECT_ID('stats'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_STATS_TABLE_DATE
        ON stats (mssql_table, insert_date DESC)
        INCLUDE (business_date, oracle_count, mongo_count);
END
GO

PRINT 'T24 Monitor — vues et index créés avec succès.';
GO
