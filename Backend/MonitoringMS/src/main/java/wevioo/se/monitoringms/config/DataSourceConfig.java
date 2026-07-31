package wevioo.se.monitoringms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * Gabarits JDBC utilisés pour interroger la base décisionnelle SQL Server.
 *
 * <p>La source de données elle-même est fournie par la configuration
 * automatique de Spring Boot, à partir des propriétés
 * {@code spring.datasource.*}. Cette classe se contente d'exposer les deux
 * gabarits attendus par les repositories.</p>
 */
@Configuration
public class DataSourceConfig {


    /**
     * Gabarit JDBC principal, utilisé par l'ensemble des repositories.
     *
     * <p>La taille de lot de récupération est portée à 500 lignes : les
     * requêtes de monitoring ramènent des volumes importants et la valeur par
     * défaut du pilote multiplierait les allers-retours réseau.</p>
     *
     * @param dataSource source de données configurée par Spring Boot
     * @return le gabarit JDBC, déclaré principal pour l'injection par type
     */
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate tpl = new JdbcTemplate(dataSource);
        tpl.setFetchSize(500);
        return tpl;
    }


    /**
     * Gabarit JDBC à paramètres nommés, pour les requêtes où la lisibilité
     * prime sur les paramètres positionnels.
     *
     * @param dataSource source de données configurée par Spring Boot
     * @return le gabarit à paramètres nommés
     */
    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
