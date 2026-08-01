package com.t24extractor.bi_sync.step;

import com.t24extractor.bi_sync.tasklet.BISyncTasklet;
import com.t24extractor.repository.bi_repos.MetaDataRepository;
import com.t24extractor.repository.bi_repos.ScheduledOpsRepository;
import com.t24extractor.repository.bi_repos.SchemaSourceAuditRepository;
import com.t24extractor.repository.bi_repos.SchemaSourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;


import static com.t24extractor.commons.Constants.*;

/**
 * Déclaration autonome du step {@code BI_SYNC}.
 *
 * <p>Variante alimentée depuis le contexte de <i>step</i> et non de <i>job</i>,
 * utilisable dans un contexte partitionné où chaque partition dispose de son
 * propre dictionnaire.
 */
@Slf4j
@Configuration
public class BISyncStep {


    /**
     * @param ssMap                       dictionnaire STANDARD.SELECTION de la partition
     * @param schemaSourceRepository      référentiel des colonnes
     * @param schemaSourceAuditRepository changements de schéma en attente
     * @param metaDataRepository          registre des champs multivalués
     * @param scheduledOpsRepository      opérations de maintenance différées
     * @param jdbcTemplate                accès à la base BI
     * @return le tasklet configuré pour la table traitée
     */
    @Bean
    @StepScope
    public BISyncTasklet biSyncTasklet(
            @Value("#{stepExecutionContext['" + BI_STANDARD_SELECTION + "']}") JSONObject ssMap,
            SchemaSourceRepository schemaSourceRepository,
            SchemaSourceAuditRepository schemaSourceAuditRepository,
            MetaDataRepository metaDataRepository,
            ScheduledOpsRepository scheduledOpsRepository,
            @Qualifier("jdbcBI") JdbcTemplate jdbcTemplate) {
        BISyncTasklet tasklet = new BISyncTasklet(
                schemaSourceRepository,
                schemaSourceAuditRepository,
                metaDataRepository,
                scheduledOpsRepository,
                jdbcTemplate);
        tasklet.setSs(ssMap);
        tasklet.setT24TableName("CUSTOMER");
        tasklet.setColonneRef("DATE_TIME");
        return tasklet;
    }

    /**
     * Déclaration du step qui contient le tasklet BISyncTasklet.
     *
     * @return step BI_SYNC
     */


    @Bean
    public Step bISync(BISyncTasklet biSyncTasklet, JobRepository jobRepository,@Qualifier("biTransactionManager") PlatformTransactionManager platformTransactionManager) {
        return new StepBuilder(String.join("", BI_SYNC), jobRepository)
                .tasklet(biSyncTasklet, platformTransactionManager)
                .build();
    }

}
