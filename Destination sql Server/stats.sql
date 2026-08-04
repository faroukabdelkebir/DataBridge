create table stats
(
    job_execution_id   bigint not null
        primary key,
    mssql_table        varchar(150),
    business_date      int,
    oracle_count       int,
    oracle_chunk_count int,
    mongo_count        int,
    insert_date        bigint default replace(
            replace(replace(CONVERT([varchar](19), sysdatetime(), 20), '-', ''), ':', ''), ' ', '')
)
go

