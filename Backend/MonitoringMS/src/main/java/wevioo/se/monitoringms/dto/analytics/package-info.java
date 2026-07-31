/**
 * Objets de transfert des indicateurs métier, pour les quatre pages
 * analytiques : Synthèse, Flux, Clients et Créances.
 *
 * <p>Trois familles s'y côtoient : les bandeaux d'indicateurs, un par page ;
 * les tranches de répartition, {@code BreakdownSliceDto} pour les effectifs et
 * {@code FlowSliceDto} lorsque des montants s'y ajoutent ; et les séries et
 * enseignements, {@code MonthlyFlowDto}, {@code HeatmapCellDto} et
 * {@code InsightDto}.</p>
 *
 * <p><strong>Convention des valeurs absentes.</strong> Un champ à {@code null}
 * signifie que la colonne source n'est pas encore extraite de T24, et se
 * distingue donc d'une valeur nulle mesurée. Une liste vide obéit à la même
 * logique. Le front s'en sert pour n'afficher que les indicateurs
 * effectivement disponibles et proposer, via les points d'accès
 * {@code columns-status}, les extractions à enrichir.</p>
 *
 * <p><strong>Séries mensuelles.</strong> Elles comptent systématiquement douze
 * points, les mois sans donnée étant remplis à zéro, à l'exception des séries
 * dont la colonne source est absente : celles-ci sont renvoyées vides.</p>
 */
package wevioo.se.monitoringms.dto.analytics;
