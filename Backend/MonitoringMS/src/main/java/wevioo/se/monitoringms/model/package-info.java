/**
 * Modèles reflétant les tables lues dans la base décisionnelle.
 *
 * <p>Chaque classe correspond à une table du microservice d'extraction :
 * métadonnées Spring Batch, comptages de réconciliation, dictionnaire de
 * schéma et paramétrage des extractions. Le mapping est réalisé manuellement
 * par les {@code RowMapper} du paquet
 * {@link wevioo.se.monitoringms.repository} ; aucune annotation de persistance
 * n'est présente.</p>
 *
 * <p>Les modèles restent proches de la structure des tables, y compris dans
 * leurs particularités : compteurs nullables, indicateurs stockés en entier,
 * dates au format numérique. Les quelques méthodes qu'ils portent se limitent
 * à des calculs dérivés immédiats — durée, débit, taux — la logique métier
 * relevant des services.</p>
 *
 * <p>Ces modèles ne sont pas exposés directement par l'API, sauf pour le
 * paramétrage et le dictionnaire de schéma ; les autres sont convertis en DTO
 * du paquet {@link wevioo.se.monitoringms.dto}.</p>
 */
package wevioo.se.monitoringms.model;
