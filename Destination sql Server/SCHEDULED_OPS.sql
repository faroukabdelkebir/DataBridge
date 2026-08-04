create table SCHEDULED_OPS
(
    id          varchar(30) not null
        constraint scheduled_ops_pk
            primary key,
    table_name  varchar(100),
    column_name varchar(90),
    ops         char,
    [ status]   varchar(2)
)
go

