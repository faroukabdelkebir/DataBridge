create table BATCH_CONFIG
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
    enabled          bit default 1
)
go

INSERT INTO T24_TEST.dbo.BATCH_CONFIG (id, TableName, T24SelectionName, colonne_ref, chargement, last_date, min_date, enabled) VALUES (N'AA.BILL.DETAILS', N'FBNK_AA_BILL_DETAILS', N'AA.BILL.DETAILS', N'PAYMENT_DATE', N'Incremental', 0, null, 1);
INSERT INTO T24_TEST.dbo.BATCH_CONFIG (id, TableName, T24SelectionName, colonne_ref, chargement, last_date, min_date, enabled) VALUES (N'CUSTOMER', N'FBNK_CUSTOMER', N'CUSTOMER', null, N'Full', null, null, 1);
INSERT INTO T24_TEST.dbo.BATCH_CONFIG (id, TableName, T24SelectionName, colonne_ref, chargement, last_date, min_date, enabled) VALUES (N'FUNDS.TRANSFER$HIS', N'FBNK_FUNDS_TRANSFER001', N'FUNDS.TRANSFER$HIS', N'DEBIT_VALUE_DATE', N'Incremental', 0, null, 1);
