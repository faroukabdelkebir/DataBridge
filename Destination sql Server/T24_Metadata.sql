create table T24_Metadata
(
    Table_Name       varchar(255) not null,
    MultiValueFields varchar(255) not null,
    LOCAL_REF        bit,
    constraint key_fields_1
        primary key (Table_Name, MultiValueFields)
)
go

INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'ADJ_AMT_BNK', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'ADJUST_AMT', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'ADJUST_AMT_LCY', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'ADJUST_REF', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'AGING_REF', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'AGING_ST_CHG_DT', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'AGING_STATUS', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'BILL_DATE', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'BILL_FINAL_DATE', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'BILL_PROPERTY', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'BILL_ST_CHG_DT', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'BILL_STATUS', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'BILL_TYPE', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'CAPTURE_PROPERTY', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'CAPTURE_PROPERTY_AMT', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'CHARGEOFF_AMOUNT', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'CHARGEOFF_REF', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'CONSOL_PROP_AMT', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'CONSOL_PROPERTY', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'DELIN_AMT', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'AA_BILL_DETAILS', N'DELIN_REP_REF', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'CUSTOMER', N'ADDR_LOCATION', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'CUSTOMER', N'ADDRESS', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'CUSTOMER', N'ADDRESS_ITEM1', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'CUSTOMER', N'ADDRESS_ITEM2', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'CUSTOMER', N'ALT_CUS_ID', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'CUSTOMER', N'ALTER_ID_NO', 1);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'CUSTOMER', N'ALTER_ID_TYPE', 1);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'CUSTOMER', N'ANNUAL_BONUS', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'CUSTOMER', N'ASS_ENTITIES', 1);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'CUSTOMER', N'AUDITOR_NAME', 1);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'CUSTOMER', N'AUDITOR_TEL', 1);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'CUSTOMER', N'AUTO_UPD_DEL_ADD', 0);
INSERT INTO T24_TEST.dbo.T24_Metadata (Table_Name, MultiValueFields, LOCAL_REF) VALUES (N'CUSTOMER', N'BANK_BRANCH', 1);
