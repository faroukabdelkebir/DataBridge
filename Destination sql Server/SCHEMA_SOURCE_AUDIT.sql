create table SCHEMA_SOURCE_AUDIT
(
    id                int identity
        primary key,
    sourceId          nvarchar(355),
    sourceTable_name  varchar(155)     not null,
    sourceColumn_name varchar(155)     not null,
    sourceDatatype    varchar(100),
    newSource_enabled bit    default 0 not null,
    oldSource_enabled bit    default 0 not null,
    newSourceIndexed  bit    default 0 not null,
    oldSourceIndexed  bit    default 0 not null,
    UpdatedBy         nvarchar(155),
    UpdatedOn         bigint default replace(
            replace(replace(CONVERT([varchar](19), sysdatetime(), 20), '-', ''), ':', ''), ' ', '')
)
go

