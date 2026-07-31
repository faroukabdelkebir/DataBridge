/**
 * Configuration technique du microservice.
 *
 * <p>{@link wevioo.se.monitoringms.config.CacheConfig} déclare les caches
 * Caffeine avec une durée de rétention propre à chaque famille de données.
 * Les caches n'étant pas créés à la demande, toute nouvelle valeur référencée
 * par une annotation {@code @Cacheable} doit y être ajoutée, sous peine
 * d'erreur à la première invocation.</p>
 *
 * <p>{@link wevioo.se.monitoringms.config.DataSourceConfig} expose les
 * gabarits JDBC utilisés par les repositories. La source de données est
 * fournie par la configuration automatique de Spring Boot, à partir des
 * propriétés {@code spring.datasource.*}.</p>
 *
 * <p>Le reste de la configuration — annuaire de services, points de
 * surveillance, documentation OpenAPI — repose sur les valeurs par défaut et
 * les propriétés déclarées dans {@code application.properties}.</p>
 */
package wevioo.se.monitoringms.config;
