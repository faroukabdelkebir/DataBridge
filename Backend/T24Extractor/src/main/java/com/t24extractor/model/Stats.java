package com.t24extractor.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import static com.t24extractor.commons.Constants.STATS;


/**
 * Volumétrie d'une exécution, pour rapprocher la source de la cible.
 *
 * <p>Comparer le compte lu sur Oracle et le compte écrit en BI permet de
 * détecter une extraction partielle.
 */
@Table(value = STATS)
public class Stats{

        @Id
        @Column("job_execution_id")
        long jobExecutionId;

        @Column("mssql_table")
        String mssqlTable;

        @Column("business_date")
        int businessDate;

        @Column("oracle_count")
        int oracleTotalCount;

        @Column("oracle_chunk_count")
        int oracleChunkCount;

        @Column("mongo_count")
        int mongoCount;
}
