/**
 * Accès JDBC à la base décisionnelle SQL Server.
 *
 * <p>Les repositories s'appuient sur {@code JdbcTemplate} et des
 * {@code RowMapper} écrits à la main : aucune couche de correspondance
 * objet-relationnel n'est employée, les tables interrogées appartenant au
 * microservice d'extraction et n'ayant pas vocation à être pilotées ici.</p>
 *
 * <p>Deux domaines sont couverts : les métadonnées Spring Batch, pour le suivi
 * technique des traitements, et les tables T24 extraites, pour l'analytique
 * métier.</p>
 *
 * <p><strong>Portabilité.</strong> Le SQL est spécifique à SQL Server —
 * pagination {@code OFFSET … FETCH NEXT}, fonctions {@code DATEADD},
 * {@code GETDATE}, {@code TRY_CONVERT}. Un changement de moteur imposerait de
 * réécrire ces requêtes.</p>
 *
 * <p><strong>Dégradation contrôlée.</strong>
 * {@link wevioo.se.monitoringms.repository.AnalyticsRepository} sonde la
 * présence des colonnes et tables satellites avant d'émettre une requête, afin
 * que les indicateurs reposant sur une extraction non encore enrichie
 * retournent une valeur absente plutôt qu'une erreur.</p>
 */
package wevioo.se.monitoringms.repository;
