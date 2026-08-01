package com.t24extractor.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jdbc.core.convert.DataAccessStrategy;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.dialect.JdbcOracleDialect;
import org.springframework.data.jdbc.core.dialect.JdbcSqlServerDialect;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * L'extracteur pilote DEUX bases hétérogènes : Oracle (source T24) et SQL
 * Server (cible BI). Chacune a sa propre pile Spring Data JDBC — dialecte,
 * mapping context, converter, stratégie d'accès, gestionnaire de transactions.
 *
 * Un croisement entre les deux (dialecte Oracle appliqué à SQL Server, ou
 * transaction BI ouverte sur la connexion T24) produit des erreurs SQL tardives
 * et difficiles à diagnostiquer. Ces tests figent la séparation.
 */
class DataSourceConfigurationTest {

    private final BIDataSourceConfiguration bi = new BIDataSourceConfiguration();
    private final T24DataSourceConfiguration t24 = new T24DataSourceConfiguration();

    // ── Dialectes : la distinction essentielle ───────────────────────────────

    @Test
    void eachSideUsesItsOwnDialect() {
        assertEquals(JdbcSqlServerDialect.INSTANCE, bi.biDialect());
        assertEquals(JdbcOracleDialect.INSTANCE, t24.t24Dialect());
    }

    // ── DataSources ──────────────────────────────────────────────────────────

    @Test
    void biDataSourceIsBuiltFromItsOwnPropertiesWithAutoCommit() {
        BIDataSourceConfiguration configuration = biConfiguredWith(
                "jdbc:sqlserver://bi-host;databaseName=BI", "bi-user", "bi-pass",
                "com.microsoft.sqlserver.jdbc.SQLServerDriver");

        try (HikariDataSource dataSource = (HikariDataSource) configuration.biDataSource()) {
            assertEquals("jdbc:sqlserver://bi-host;databaseName=BI", dataSource.getJdbcUrl());
            assertEquals("bi-user", dataSource.getUsername());
            // L'auto-commit est requis côté BI : les DDL générés (CREATE/ALTER)
            // sont exécutés hors transaction batch.
            assertTrue(dataSource.isAutoCommit());
            assertEquals(20, dataSource.getMaximumPoolSize());
        }
    }

    @Test
    void t24DataSourceIsBuiltFromItsOwnProperties() {
        T24DataSourceConfiguration configuration = t24ConfiguredWith(
                "jdbc:oracle:thin:@//t24-host:1521/T24", "t24-user", "t24-pass",
                "oracle.jdbc.OracleDriver");

        try (HikariDataSource dataSource = (HikariDataSource) configuration.t24DataSource()) {
            assertEquals("jdbc:oracle:thin:@//t24-host:1521/T24", dataSource.getJdbcUrl());
            assertEquals("t24-user", dataSource.getUsername());
            assertEquals(20, dataSource.getMaximumPoolSize());
        }
    }

    @Test
    void biDataSourceFailsFastWhenConfigurationIsIncomplete() {
        // Sans URL, mieux vaut échouer au démarrage qu'au premier job nocturne.
        assertThrows(NullPointerException.class,
                () -> biConfiguredWith(null, "user", "pass", "driver").biDataSource());
    }

    @Test
    void exposesEmptyPropertiesHoldersForBinding() {
        assertNotNull(bi.biDataSourceProperties());
        assertNotNull(t24.t24DataSourceProperties());
    }

    // ── Templates et transactions ────────────────────────────────────────────

    @Test
    void bindsEachTemplateAndTransactionManagerToItsOwnDataSource() {
        DataSource biDs = mock(DataSource.class);
        DataSource t24Ds = mock(DataSource.class);

        assertEquals(biDs, bi.jdbcSqlServer(biDs).getDataSource());
        assertEquals(t24Ds, t24.jdbcTemplateLogic(t24Ds).getDataSource());

        assertEquals(biDs, ((DataSourceTransactionManager) bi.biTransactionManager(biDs)).getDataSource());
        assertEquals(t24Ds, ((DataSourceTransactionManager) t24.t24TransactionManager(t24Ds)).getDataSource());

        assertNotNull(bi.biJdbcOperations(biDs));
        assertNotNull(t24.t24JdbcOperations(t24Ds));
    }

    @Test
    void batchInfrastructureRunsOnTheBiSideOnly() {
        DataSource biDs = mock(DataSource.class);
        DataSourceTransactionManager biTx = new DataSourceTransactionManager(biDs);
        BatchInfrastructureConfiguration infrastructure = new BatchInfrastructureConfiguration(biDs, biTx);

        // Les métadonnées Spring Batch vivent dans la base BI : les pointer vers
        // Oracle (lecture seule) ferait échouer toute écriture de JobRepository.
        assertEquals(biDs, ReflectionTestUtils.invokeMethod(infrastructure, "getDataSource"));
        assertEquals(biTx, ReflectionTestUtils.invokeMethod(infrastructure, "getTransactionManager"));
    }

    // ── Piles Spring Data JDBC ───────────────────────────────────────────────

    @Test
    void buildsAnIndependentPersistenceStackPerDatabase() {
        assertNotNull(biStack());
        assertNotNull(t24Stack());
    }

    @Test
    void mappingContextsAreNotShared() {
        // Deux contextes distincts : une entité mappée côté BI ne doit pas
        // hériter du dialecte Oracle et inversement.
        RelationalMappingContext biContext = bi.biMappingContext();
        RelationalMappingContext t24Context = t24.t24MappingContext();

        org.junit.jupiter.api.Assertions.assertNotSame(biContext, t24Context);
        assertNotNull(bi.biJdbcCustomConversions());
        assertNotNull(t24.t24JdbcCustomConversions());
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    private Object biStack() {
        RelationalMappingContext context = bi.biMappingContext();
        NamedParameterJdbcOperations ops = bi.biJdbcOperations(mock(DataSource.class));
        JdbcConverter converter = bi.biJdbcConverter(context, ops, mock(org.springframework.data.jdbc.core.convert.RelationResolver.class), bi.biJdbcCustomConversions());
        DataAccessStrategy strategy = bi.biDataAccessStrategy(context, converter, ops, bi.biDialect());
        return bi.biJdbcAggregateOperations(applicationContext(), context, converter, strategy);
    }

    private Object t24Stack() {
        RelationalMappingContext context = t24.t24MappingContext();
        NamedParameterJdbcOperations ops = t24.t24JdbcOperations(mock(DataSource.class));
        JdbcConverter converter = t24.t24JdbcConverter(context, ops, mock(org.springframework.data.jdbc.core.convert.RelationResolver.class), t24.t24JdbcCustomConversions());
        DataAccessStrategy strategy = t24.t24DataAccessStrategy(context, converter, ops, t24.t24Dialect());
        return t24.t24JdbcAggregateOperations(applicationContext(), context, converter, strategy);
    }

    private BIDataSourceConfiguration biConfiguredWith(
            String url, String username, String password, String driver) {
        DataSourceProperties properties = properties(url, username, password, driver);
        return new BIDataSourceConfiguration() {
            @Override
            public DataSourceProperties biDataSourceProperties() {
                return properties;
            }
        };
    }

    private T24DataSourceConfiguration t24ConfiguredWith(
            String url, String username, String password, String driver) {
        DataSourceProperties properties = properties(url, username, password, driver);
        return new T24DataSourceConfiguration() {
            @Override
            public DataSourceProperties t24DataSourceProperties() {
                return properties;
            }
        };
    }

    /** JdbcAggregateTemplate résout ses EntityCallbacks via le BeanFactory réel. */
    private ApplicationContext applicationContext() {
        return new org.springframework.context.support.StaticApplicationContext();
    }

    private DataSourceProperties properties(String url, String username, String password, String driver) {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl(url);
        properties.setUsername(username);
        properties.setPassword(password);
        properties.setDriverClassName(driver);
        return properties;
    }
}
