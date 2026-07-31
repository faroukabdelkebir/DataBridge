/**
 * Contrôleurs REST exposant l'API de supervision sous {@code /api/v1}.
 *
 * <p>Les contrôleurs restent volontairement minces : ils valident et bornent
 * les paramètres de requête, puis délèguent aux services. La documentation
 * OpenAPI est portée par les annotations {@code @Operation} et
 * {@code @Parameter}.</p>
 *
 * <p>Découpage des ressources :</p>
 * <ul>
 *   <li>{@code /dashboard} — vue d'ensemble et traitements en cours ;</li>
 *   <li>{@code /jobs} — historique des exécutions et détail des étapes ;</li>
 *   <li>{@code /kpis} — indicateurs, réconciliation et fenêtres SLA ;</li>
 *   <li>{@code /alerts} — alertes actives et acquittement ;</li>
 *   <li>{@code /analytics} — indicateurs métier des quatre pages analytiques ;</li>
 *   <li>{@code /schema} — dictionnaire de schéma T24 et son audit ;</li>
 *   <li>{@code /config} — paramétrage des extractions ;</li>
 *   <li>{@code /reports} — rapports journalier et hebdomadaire ;</li>
 *   <li>{@code /stream} — flux d'événements serveur temps réel.</li>
 * </ul>
 *
 * <p><strong>Convention de réponse.</strong> Une ressource absente donne un
 * {@code 404} ; une collection sans résultat donne un tableau vide avec le
 * statut {@code 200}, l'absence de donnée n'étant pas une erreur.</p>
 */
package wevioo.se.monitoringms.controller;
