-- Create Table stats
IF
NOT EXISTS(SELECT *
              FROM sysobjects
              WHERE name = 'stats'
                and xtype = 'U')
CREATE TABLE stats
(
    job_execution_id   bigint NOT NULL PRIMARY KEY,
    mssql_table        varchar(150) NULL,
    business_date      int NULL,
    oracle_count       int NULL,
    oracle_chunk_count int NULL,
    mongo_count        int NULL,
    insert_date        bigint default replace(
            replace(replace(CONVERT(varchar (19), sysdatetime(), 20), '-', ''), ':', ''),
            ' ', '')
);
GO
-- Create Table T24_Metadata
IF NOT EXISTS(SELECT *
              FROM sysobjects
              WHERE name = 'T24_Metadata'
                and xtype = 'U')
CREATE TABLE T24_Metadata
(
    Table_Name       varchar(255) NOT NULL,
    MultiValueFields varchar(255) NOT NULL,
    LOCAL_REF        bit NULL,
    CONSTRAINT key_fields_1 PRIMARY KEY (Table_Name, MultiValueFields)
);
GO
-- CREATE Table Batch_Config

IF NOT EXISTS(SELECT *
              FROM sysobjects
              WHERE name = 'BATCH_CONFIG'
                and xtype = 'U')
CREATE TABLE BATCH_CONFIG
(
    id               varchar(30) not null
        constraint batch_config_pk
            primary key,
    TableName        varchar(100),
    T24SelectionName varchar(90),
    colonne_ref      varchar(100),
    chargement       varchar(100),
    last_date        bigint,
    min_date         bigint,
    enabled          bit default 1,
    scheduler        bit default 0,
    scheduler_time   varchar(8) null
);
GO

IF COL_LENGTH('dbo.BATCH_CONFIG', 'scheduler') IS NULL
BEGIN
    ALTER TABLE dbo.BATCH_CONFIG ADD scheduler bit NOT NULL CONSTRAINT DF_BATCH_CONFIG_scheduler DEFAULT 0;
END
GO

IF COL_LENGTH('dbo.BATCH_CONFIG', 'scheduler_time') IS NULL
BEGIN
    ALTER TABLE dbo.BATCH_CONFIG ADD scheduler_time varchar(8) NULL;
END
GO

-- CREATE Table scheduled_ops
IF NOT EXISTS(SELECT *
              FROM sysobjects
              WHERE name = 'SCHEDULED_OPS'
                and xtype = 'U')
CREATE TABLE SCHEDULED_OPS
(
    id          varchar(30) not null
        constraint scheduled_ops_pk
            primary key,
    table_name  varchar(100),
    column_name varchar(90),
    ops         char,
    [status]    varchar(2)
);
GO

-- CREATE TABLE SchemaSource
IF NOT EXISTS(SELECT *
              FROM sysobjects
              WHERE name = 'SCHEMA_SOURCE'
                and xtype = 'U')
CREATE TABLE SCHEMA_SOURCE
(
    id          varchar(355) PRIMARY KEY,
    table_name  varchar(155) not null,
    column_name varchar(155) not null,
    datatype    varchar(100) not null,
    _enabled    bit          not null default 0,
    indexed     bit          not null default 0,
    ssTag       varchar(10)  not null,
    single_mult char         not null,
    local_ref   bit          not null,
    insert_date bigint                default replace(
            replace(replace(CONVERT(varchar (19), sysdatetime(), 20), '-', ''), ':', ''), ' ', ''),
    INDEX       tab_col_idx NONCLUSTERED (table_name, column_name),
    INDEX       tab_enable_idx NONCLUSTERED (table_name, _enabled)

);
GO

-- CREATE TABLE schemaSourceAudit

IF NOT EXISTS(SELECT *
              FROM sysobjects
              WHERE name = 'SCHEMA_SOURCE_AUDIT'
                and xtype = 'U')
CREATE TABLE SCHEMA_SOURCE_AUDIT
(
    id                int          NOT NULL IDENTITY (1,1) PRIMARY KEY,
    sourceId          nvarchar(355),
    sourceTable_name  varchar(155) not null,
    sourceColumn_name varchar(155) not null,
    sourceDatatype    varchar(100),
    newSource_enabled bit          NOT NULL default 0,
    oldSource_enabled bit          NOT NULL default 0,
    newSourceIndexed  bit          not null default 0,
    oldSourceIndexed  bit          not null default 0,
    UpdatedBy         nvarchar(155),
    UpdatedOn         bigint                default replace(
            replace(replace(CONVERT(varchar (19), sysdatetime(), 20), '-', ''), ':', ''), ' ', '')
);
GO

-- CREATE TRIGGER schemaSourceTriggerAudit

create
or alter
trigger schemaSourceTriggerAudit on SCHEMA_SOURCE
    after
update
    as
BEGIN
insert into SCHEMA_SOURCE_AUDIT
(sourceId, sourceTable_name, sourceColumn_name, sourceDatatype, newSource_enabled, oldSource_enabled, newSourceIndexed,
 oldSourceIndexed, UpdatedBy)
select i.id,
       i.table_name,
       i.column_name,
       i.datatype,
       i._enabled,
       d._enabled,
       i.indexed,
       d.indexed,
       SUSER_SNAME()
from inserted i
         inner join deleted d on d.id = i.id
END
GO

-- Spring Batch 6 metadata sequences (required by SQL Server incrementer strategy)
IF NOT EXISTS (SELECT * FROM sys.sequences WHERE name = 'BATCH_STEP_EXECUTION_SEQ')
BEGIN
    CREATE SEQUENCE BATCH_STEP_EXECUTION_SEQ
        START WITH 0
        MINVALUE 0
        MAXVALUE 9223372036854775807
        NO CACHE
        NO CYCLE;
END
GO

IF NOT EXISTS (SELECT * FROM sys.sequences WHERE name = 'BATCH_JOB_EXECUTION_SEQ')
BEGIN
    CREATE SEQUENCE BATCH_JOB_EXECUTION_SEQ
        START WITH 0
        MINVALUE 0
        MAXVALUE 9223372036854775807
        NO CACHE
        NO CYCLE;
END
GO

IF NOT EXISTS (SELECT * FROM sys.sequences WHERE name = 'BATCH_JOB_INSTANCE_SEQ')
BEGIN
    CREATE SEQUENCE BATCH_JOB_INSTANCE_SEQ
        START WITH 0
        MINVALUE 0
        MAXVALUE 9223372036854775807
        NO CACHE
        NO CYCLE;
END
GO

BEGIN
   IF NOT EXISTS (SELECT * FROM dbo.BATCH_CONFIG
                   WHERE id = N'AA.BILL.DETAILS'
                   )
BEGIN
INSERT INTO dbo.BATCH_CONFIG (id, TableName, T24SelectionName, colonne_ref, chargement, last_date, min_date, enabled, scheduler, scheduler_time)
VALUES
    (N'AA.BILL.DETAILS', N'FBNK_AA_BILL_DETAILS', N'AA.BILL.DETAILS', N'PAYMENT_DATE', N'Incremental', 0, null, 1, 0, null)
END
END
GO

BEGIN
   IF NOT EXISTS (SELECT * FROM dbo.BATCH_CONFIG
                   WHERE id = N'CUSTOMER'
                   )
BEGIN
INSERT INTO dbo.BATCH_CONFIG (id, TableName, T24SelectionName, colonne_ref, chargement, last_date, min_date, enabled, scheduler, scheduler_time)
VALUES (N'CUSTOMER', N'FBNK_CUSTOMER', N'CUSTOMER', null, N'Full', null, null, 1, 0, null)
END
END
GO

BEGIN
   IF NOT EXISTS (SELECT * FROM dbo.BATCH_CONFIG
                   WHERE id = N'FUNDS.TRANSFER$HIS'
                   )
BEGIN
INSERT INTO dbo.BATCH_CONFIG (id, TableName, T24SelectionName, colonne_ref, chargement, last_date, min_date, enabled, scheduler, scheduler_time)
VALUES (N'FUNDS.TRANSFER$HIS', N'FBNK_FUNDS_TRANSFER001', N'FUNDS.TRANSFER$HIS', N'DEBIT_VALUE_DATE', N'Incremental', 0, null, 1, 0, null)
END
END
GO
