package com.t24extractor.configuration;


import com.t24extractor.model.CobStatus;
import com.t24extractor.model.FileControl;
import com.t24extractor.model.StandardSelectionRecord;
import com.t24extractor.model.TafjVoc;
import com.t24extractor.repository.t24_repo.CobRepository;
import com.t24extractor.repository.t24_repo.FileControlRepository;
import com.t24extractor.repository.t24_repo.StandardSelectionRepository;
import com.t24extractor.repository.t24_repo.TafjVocRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.data.jdbc.core.convert.*;
import org.springframework.data.jdbc.core.dialect.JdbcOracleDialect;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.data.relational.core.mapping.DefaultNamingStrategy;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@EnableJdbcRepositories(
        basePackageClasses = {StandardSelectionRecord.class, StandardSelectionRepository.class, FileControl.class, FileControlRepository.class, TafjVoc.class, TafjVocRepository.class, CobRepository.class, CobStatus.class},
        basePackages = "com.t24extractor.repository.t24_repo",
        jdbcAggregateOperationsRef = "t24JdbcAggregateOperations",
        transactionManagerRef = "t24TransactionManager"
)

/**
 * Pile d'accès à la base T24 (Oracle), source de l'extraction.
 *
 * <p>Symétrique de {@link BIDataSourceConfiguration}, mais non {@code @Primary} :
 * chaque bean doit être réclamé explicitement par son qualificateur
 * ({@code t24}, {@code jdbcTemplateT24}, {@code t24TransactionManager}), ce qui
 * évite qu'une injection par défaut ne vise Oracle par mégarde.
 *
 * <p>Seuls les dictionnaires T24 sont mappés en entités ; les données métier
 * sont lues par requêtes directes, leur structure n'étant connue qu'à
 * l'exécution.
 */
public class T24DataSourceConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.t24")
    public DataSourceProperties t24DataSourceProperties() {

        return new DataSourceProperties();
    }

    @Bean(name = "t24TransactionManager")
    public PlatformTransactionManager t24TransactionManager(@Qualifier("t24") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }

    @Bean(name = "t24")
    public DataSource t24DataSource() {
        HikariDataSource ds = new HikariDataSource();

        ds.setJdbcUrl(t24DataSourceProperties().getUrl());
        ds.setDriverClassName(t24DataSourceProperties().getDriverClassName());
        ds.setUsername(t24DataSourceProperties().getUsername());
        ds.setPassword(t24DataSourceProperties().getPassword());
        ds.setMaximumPoolSize(20);
        return ds;
    }

    @Bean(name = "jdbcTemplateT24")
    public JdbcTemplate jdbcTemplateLogic(@Qualifier("t24") DataSource ds) {

        return new JdbcTemplate(ds);
    }


    @Bean(name = "t24JdbcOperations")
    NamedParameterJdbcOperations t24JdbcOperations(@Qualifier("t24") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean(name = "t24Dialect")
    public Dialect t24Dialect() {
        return JdbcOracleDialect.INSTANCE;
    }

    @Bean(name = "t24JdbcCustomConversions")
    public JdbcCustomConversions t24JdbcCustomConversions() {
        return new JdbcCustomConversions();
    }

    @Bean(name = "t24MappingContext")
    public RelationalMappingContext t24MappingContext() {
        return new org.springframework.data.jdbc.core.mapping.JdbcMappingContext(DefaultNamingStrategy.INSTANCE);
    }

    @Bean(name = "t24JdbcConverter")
    public JdbcConverter t24JdbcConverter(
            @Qualifier("t24MappingContext") RelationalMappingContext context,
            @Qualifier("t24JdbcOperations") NamedParameterJdbcOperations ops,
            @Lazy @Qualifier("t24DataAccessStrategy") RelationResolver relationResolver,
            @Qualifier("t24JdbcCustomConversions") JdbcCustomConversions conversions
    ) {
        return new MappingJdbcConverter(context, relationResolver, conversions, new DefaultJdbcTypeFactory(ops.getJdbcOperations()));
    }

    @Bean(name = "t24DataAccessStrategy")
    public DataAccessStrategy t24DataAccessStrategy(
            @Qualifier("t24MappingContext") RelationalMappingContext context,
            @Qualifier("t24JdbcConverter") JdbcConverter converter,
            @Qualifier("t24JdbcOperations") NamedParameterJdbcOperations ops,
            @Qualifier("t24Dialect") Dialect dialect
    ) {
        var sqlGeneratorSource = new SqlGeneratorSource(context, converter, dialect);
        return new DefaultDataAccessStrategy(
                sqlGeneratorSource,
                context,
                converter,
                ops,
                new org.springframework.data.jdbc.core.convert.SqlParametersFactory(context, converter),
                new org.springframework.data.jdbc.core.convert.InsertStrategyFactory(ops, dialect),
                org.springframework.data.jdbc.core.convert.QueryMappingConfiguration.EMPTY
        );
    }

    @Bean(name = "t24JdbcAggregateOperations")
    public JdbcAggregateOperations t24JdbcAggregateOperations(
            ApplicationContext applicationContext,
            @Qualifier("t24MappingContext") RelationalMappingContext context,
            @Qualifier("t24JdbcConverter") JdbcConverter converter,
            @Qualifier("t24DataAccessStrategy") DataAccessStrategy dataAccessStrategy
    ) {
        return new JdbcAggregateTemplate(applicationContext, context, converter, dataAccessStrategy);
    }
}
