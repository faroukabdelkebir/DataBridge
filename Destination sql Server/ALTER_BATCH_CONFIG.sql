IF NOT EXISTS (SELECT 1 FROM sys.columns 
               WHERE object_id = OBJECT_ID('BATCH_CONFIG') AND name = 'scheduler')
BEGIN
    ALTER TABLE BATCH_CONFIG ADD scheduler BIT NOT NULL 
        CONSTRAINT DF_BATCH_CONFIG_scheduler DEFAULT 0;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns 
               WHERE object_id = OBJECT_ID('BATCH_CONFIG') AND name = 'scheduler_time')
BEGIN
    ALTER TABLE BATCH_CONFIG ADD scheduler_time VARCHAR(8) NULL;
END
GO