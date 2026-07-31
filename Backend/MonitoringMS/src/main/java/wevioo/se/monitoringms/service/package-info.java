/**
 * Services portant les règles de calcul et d'agrégation de la supervision.
 *
 * <p>Deux familles cohabitent : la supervision technique du processus
 * d'extraction — {@link wevioo.se.monitoringms.service.DashboardService},
 * {@link wevioo.se.monitoringms.service.JobService},
 * {@link wevioo.se.monitoringms.service.KpiService},
 * {@link wevioo.se.monitoringms.service.SlaService},
 * {@link wevioo.se.monitoringms.service.ReconciliationService} — et
 * l'analytique métier sur les données extraites, portée par
 * {@link wevioo.se.monitoringms.service.AnalyticsService}.</p>
 *
 * <p><strong>Mise en cache.</strong> Les méthodes coûteuses sont annotées
 * {@code @Cacheable}, avec des durées de rétention différenciées déclarées
 * dans {@link wevioo.se.monitoringms.config.CacheConfig}. Un appel interne
 * entre deux méthodes d'un même service contourne le cache, n'étant pas
 * intercepté par le mandataire Spring.</p>
 *
 * <p><strong>Traitements planifiés.</strong>
 * {@link wevioo.se.monitoringms.service.AlertService} réévalue les conditions
 * d'alerte chaque minute et
 * {@link wevioo.se.monitoringms.service.SsePublisher} diffuse le flux temps
 * réel. Ces méthodes absorbent leurs erreurs : une indisponibilité passagère
 * de la base ne doit pas interrompre la planification.</p>
 */
package wevioo.se.monitoringms.service;
