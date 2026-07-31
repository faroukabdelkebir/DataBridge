/**
 * Microservice de supervision du processus d'extraction T24 vers la base
 * décisionnelle SQL Server.
 *
 * <p>Le service est en lecture seule sur les données métier : il observe le
 * travail réalisé par le microservice d'extraction sans y prendre part. Seul
 * le paramétrage des extractions est modifiable, par activation ou
 * désactivation.</p>
 *
 * <p>L'architecture suit un découpage en couches :</p>
 * <ul>
 *   <li>{@link wevioo.se.monitoringms.controller} — exposition HTTP ;</li>
 *   <li>{@link wevioo.se.monitoringms.service} — règles de calcul et d'agrégation ;</li>
 *   <li>{@link wevioo.se.monitoringms.repository} — accès JDBC à SQL Server ;</li>
 *   <li>{@link wevioo.se.monitoringms.model} — reflet des tables interrogées ;</li>
 *   <li>{@link wevioo.se.monitoringms.dto} — contrat exposé à l'API ;</li>
 *   <li>{@link wevioo.se.monitoringms.config} — configuration technique.</li>
 * </ul>
 *
 * <p><strong>Disponibilité progressive des données.</strong> Le périmètre des
 * colonnes extraites de T24 s'enrichit au fil des livraisons. Les indicateurs
 * qui en dépendent retournent {@code null} ou une liste vide tant que la
 * colonne n'est pas disponible, plutôt que de faire échouer l'appel : le
 * {@code null} signifie « donnée indisponible » et se distingue de la valeur
 * zéro.</p>
 */
package wevioo.se.monitoringms;
