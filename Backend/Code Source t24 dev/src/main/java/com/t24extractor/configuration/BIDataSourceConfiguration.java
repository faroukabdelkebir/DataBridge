package com.t24extractor.configuration;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jdbc.core.dialect.JdbcSqlServerDialect;
import org.springframework.data.jdbc.core.convert.DefaultJdbcTypeFactory;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.convert.MappingJdbcConverter;
import org.springframework.data.jdbc.core.convert.RelationResolver;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.data.relational.core.mapping.DefaultNamingStrategy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.data.jdbc.core.convert.DataAccessStrategy;
import org.springframework.data.jdbc.core.convert.DefaultDataAccessStrategy;
import org.springframework.data.jdbc.core.convert.SqlGeneratorSource;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;

import javax.sql.DataSource;
import java.util.Objects;


@Configuration
@EnableJdbcRepositories(
        basePackages = "com.t24extractor.repository.bi_repos",
        jdbcAggregateOperationsRef = "biJdbcAggregateOperations",
        transactionManagerRef = "biTransactionManager"
)

/**
 * Pile d'accès à la base BI (SQL Server), cible de l'extraction.
 *
 * <p>Déclarée {@code @Primary} : c'est la base par défaut de l'application, y
 * compris pour les métadonnées Spring Batch. Chaque bean a son pendant côté
 * Oracle dans {@link T24DataSourceConfiguration} ; les deux piles sont
 * volontairement disjointes pour qu'un dialecte ou une transaction ne puisse
 * pas être appliqué à la mauvaise base.
 *
 * <p>L'auto-commit est activé car les instructions DDL générées à l'étape
 * BI_SYNC (CREATE, ALTER) s'exécutent hors des transactions du batch.
 */
public class BIDataSourceConfiguration {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.bi")
    public DataSourceProperties biDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "bi")
    @Primary
    public DataSource biDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(Objects.requireNonNull(biDataSourceProperties().getUrl()));
        ds.setUsername(Objects.requireNonNull(biDataSourceProperties().getUsername()));
        ds.setPassword(Objects.requireNonNull(biDataSourceProperties().getPassword()));
        ds.setDriverClassName(Objects.requireNonNull(biDataSourceProperties().getDriverClassName()));
        ds.setAutoCommit(true);
        ds.setMaximumPoolSize(20);
        return ds;
    }


    @Bean("jdbcBI")
    @Primary
    public JdbcTemplate jdbcSqlServer(@Qualifier("bi") DataSource ds) {
        return new JdbcTemplate(ds);
    }


    @Bean(name = "biJdbcOperations")
    @Primary
    NamedParameterJdbcOperations biJdbcOperations(@Qualifier("bi") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean(name = "biTransactionManager")
    @Primary
    public PlatformTransactionManager biTransactionManager(@Qualifier("bi") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }

    @Bean(name = "biDialect")
    @Primary
    public Dialect biDialect() {
        return JdbcSqlServerDialect.INSTANCE;
    }

    @Bean(name = "biJdbcCustomConversions")
    @Primary
    public JdbcCustomConversions biJdbcCustomConversions() {
        return new JdbcCustomConversions();
    }

    @Bean(name = "biMappingContext")
    @Primary
    public RelationalMappingContext biMappingContext() {
        return new org.springframework.data.jdbc.core.mapping.JdbcMappingContext(DefaultNamingStrategy.INSTANCE);
    }

    @Bean(name = "biJdbcConverter")
    @Primary
    public JdbcConverter biJdbcConverter(
            @Qualifier("biMappingContext") RelationalMappingContext context,
            @Qualifier("biJdbcOperations") NamedParameterJdbcOperations ops,
            @Lazy @Qualifier("biDataAccessStrategy") RelationResolver relationResolver,
            @Qualifier("biJdbcCustomConversions") JdbcCustomConversions conversions
    ) {
        return new MappingJdbcConverter(context, relationResolver, conversions, new DefaultJdbcTypeFactory(ops.getJdbcOperations()));
    }

    @Bean(name = "biDataAccessStrategy")
    @Primary
    public DataAccessStrategy biDataAccessStrategy(
            @Qualifier("biMappingContext") RelationalMappingContext context,
            @Qualifier("biJdbcConverter") JdbcConverter converter,
            @Qualifier("biJdbcOperations") NamedParameterJdbcOperations ops,
            @Qualifier("biDialect") Dialect dialect
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

    @Bean(name = "biJdbcAggregateOperations")
    @Primary
    public JdbcAggregateOperations biJdbcAggregateOperations(
            ApplicationContext applicationContext,
            @Qualifier("biMappingContext") RelationalMappingContext context,
            @Qualifier("biJdbcConverter") JdbcConverter converter,
            @Qualifier("biDataAccessStrategy") DataAccessStrategy dataAccessStrategy
    ) {
        return new JdbcAggregateTemplate(applicationContext, context, converter, dataAccessStrategy);
    }

}
