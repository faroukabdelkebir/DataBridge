package wevioo.se.monitoringms.controller;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import wevioo.se.monitoringms.dto.analytics.*;
import wevioo.se.monitoringms.service.AnalyticsService;

import java.util.List;
import java.util.Map;

/**
 * Endpoints analytiques métier (v2 — ajoute la page « Flux »).
 *
 * Page Synthèse :
 *   GET /api/v1/analytics/summary?year=            → KPIs + évolutions vs N-1
 *   GET /api/v1/analytics/transfers/monthly?year=  → volumes/montants mensuels N vs N-1
 *   GET /api/v1/analytics/customers/age-pyramid    → répartition par tranche d'âge
 *   GET /api/v1/analytics/customers/by-officer     → top gestionnaires
 *   GET /api/v1/analytics/billing/aging            → vieillissement des créances
 *   GET /api/v1/analytics/years                    → années disponibles (filtres)
 *   GET /api/v1/analytics/insights?year=           → enseignements automatiques
 *
 * Page Flux :
 *   GET /api/v1/analytics/flows/summary?year=        → KPIs volumes + montants
 *   GET /api/v1/analytics/flows/by-currency?year=    → répartition par devise
 *   GET /api/v1/analytics/flows/by-type?year=        → répartition par type d'opération
 *   GET /api/v1/analytics/flows/heatmap?year=        → activité mois × jour de semaine
 *   GET /api/v1/analytics/flows/columns-status       → colonnes enrichies présentes ?
 *   GET /api/v1/analytics/flows/insights?year=       → enseignements de flux
 *
 * Page Clients :
 *   GET /api/v1/analytics/customers/summary?year=      → KPIs démographie + acquisition
 *   GET /api/v1/analytics/customers/age-histogram      → âges par pas de 5 ans
 *   GET /api/v1/analytics/customers/generations        → répartition par génération
 *   GET /api/v1/analytics/customers/new-monthly?year=  → nouveaux clients N vs N-1
 *   GET /api/v1/analytics/customers/breakdown?by=      → segmentation (secteur, genre…)
 *   GET /api/v1/analytics/customers/dimensions         → dimensions déjà extraites
 *   GET /api/v1/analytics/customers/columns-status     → colonnes enrichies présentes ?
 *   GET /api/v1/analytics/customers/acquisition-years  → années d'entrée en relation
 *   GET /api/v1/analytics/customers/insights?year=     → enseignements clients
 *
 * Page Créances :
 *   GET /api/v1/analytics/billing/summary?year=     → KPIs facturation + recouvrement
 *   GET /api/v1/analytics/billing/monthly?year=     → factures N vs N-1 (+ montants)
 *   GET /api/v1/analytics/billing/breakdown?by=     → segmentation (aging, règlement…)
 *   GET /api/v1/analytics/billing/dimensions        → dimensions déjà extraites
 *   GET /api/v1/analytics/billing/columns-status    → colonnes enrichies présentes ?
 *   GET /api/v1/analytics/billing/years             → années de facturation
 *   GET /api/v1/analytics/billing/insights?year=    → enseignements créances
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytique métier", description = "Indicateurs bancaires issus des données T24 extraites")
public class AnalyticsController {

    /** Couche analytique métier. */
    private final AnalyticsService analytics;

    // ── Page Synthèse ─────────────────────────────────────────────────────────

    /**
     * Retourne le bandeau d'indicateurs de la page Synthèse.
     *
     * @param year année à analyser ; {@code 0} — valeur par défaut — couvre
     *             tout l'historique et désactive la comparaison à N-1
     * @return les indicateurs de synthèse
     */
    @GetMapping("/summary")
    @Operation(summary = "Indicateurs métier agrégés",
            description = "Clients, virements et factures avec évolution vs année précédente.")
    public ResponseEntity<AnalyticsSummaryDto> summary(
            @Parameter(description = "Année (0 = tout l'historique)")
            @RequestParam(defaultValue = "0") int year) {
        return ResponseEntity.ok(analytics.summary(year));
    }

    /**
     * Retourne la série mensuelle des virements, comparée à N-1.
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return douze points, de janvier à décembre, les mois sans opération
     *         étant remplis à zéro
     */
    @GetMapping("/transfers/monthly")
    @Operation(summary = "Volumes et montants mensuels de virements, comparés à N-1")
    public ResponseEntity<List<MonthlyFlowDto>> monthlyTransfers(
            @RequestParam(defaultValue = "0") int year) {
        return ResponseEntity.ok(analytics.monthlyTransfers(year));
    }

    /**
     * Retourne la répartition des clients par tranche d'âge.
     *
     * @return six tranches, de la plus jeune à la plus âgée
     */
    @GetMapping("/customers/age-pyramid")
    @Operation(summary = "Répartition des clients par tranche d'âge")
    public ResponseEntity<List<BreakdownSliceDto>> agePyramid() {
        return ResponseEntity.ok(analytics.agePyramid());
    }

    /**
     * Retourne les gestionnaires classés par taille de portefeuille.
     *
     * <p>Le nombre demandé est ramené dans l'intervalle de 1 à 50 : une valeur
     * hors bornes est corrigée plutôt que rejetée.</p>
     *
     * @param top nombre de gestionnaires souhaité ; vaut 10 par défaut
     * @return les gestionnaires du plus gros portefeuille au plus petit
     */
    @GetMapping("/customers/by-officer")
    @Operation(summary = "Portefeuille clients par gestionnaire (top N)")
    public ResponseEntity<List<OfficerPerfDto>> byOfficer(
            @Parameter(description = "Nombre de gestionnaires à retourner (max 50)")
            @RequestParam(defaultValue = "10") int top) {
        return ResponseEntity.ok(analytics.topOfficers(Math.clamp(top, 1, 50)));
    }

    /**
     * Retourne la répartition des créances par état de vieillissement.
     *
     * @return les tranches par effectif décroissant, ou un tableau vide tant
     *         que {@code AGING_STATUS} n'est pas extrait
     */
    @GetMapping("/billing/aging")
    @Operation(summary = "Vieillissement des créances",
            description = "Vide tant que la colonne AGING_STATUS n'a pas été extraite.")
    public ResponseEntity<List<BreakdownSliceDto>> billingAging() {
        return ResponseEntity.ok(analytics.billingAging());
    }

    /**
     * Retourne les années proposables dans le filtre des virements.
     *
     * @return les années par ordre décroissant
     */
    @GetMapping("/years")
    @Operation(summary = "Années disponibles dans l'historique de virements")
    public ResponseEntity<List<Integer>> years() {
        return ResponseEntity.ok(analytics.availableYears());
    }

    /**
     * Retourne les enseignements de la page Synthèse.
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return gestionnaire en tête, concentration du portefeuille et pic
     *         d'activité, chacun produit seulement si calculable
     */
    @GetMapping("/insights")
    @Operation(summary = "Enseignements calculés automatiquement",
            description = "Gestionnaire leader, concentration du portefeuille, pic d'activité.")
    public ResponseEntity<List<InsightDto>> insights(
            @RequestParam(defaultValue = "0") int year) {
        return ResponseEntity.ok(analytics.insights(year));
    }

    // ── Page Flux ─────────────────────────────────────────────────────────────

    /**
     * Retourne le bandeau d'indicateurs de la page Flux.
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return volumes, montants, montant moyen par opération et devise
     *         dominante ; les montants restent nuls tant que
     *         {@code AMOUNT_DEBITED} n'est pas extraite
     */
    @GetMapping("/flows/summary")
    @Operation(summary = "KPIs des flux de virements",
            description = "Volumes, montants, ticket moyen et devise dominante. "
                    + "Les montants restent à null tant que AMOUNT_DEBITED n'est pas extraite.")
    public ResponseEntity<FlowsSummaryDto> flowsSummary(
            @RequestParam(defaultValue = "0") int year) {
        return ResponseEntity.ok(analytics.flowsSummary(year));
    }

    /**
     * Retourne la répartition des virements par devise de débit.
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return les tranches par part décroissante, ou un tableau vide tant que
     *         {@code DEBIT_CURRENCY} n'est pas extraite
     */
    @GetMapping("/flows/by-currency")
    @Operation(summary = "Répartition des flux par devise",
            description = "Vide tant que DEBIT_CURRENCY n'a pas été extraite.")
    public ResponseEntity<List<FlowSliceDto>> flowsByCurrency(
            @RequestParam(defaultValue = "0") int year) {
        return ResponseEntity.ok(analytics.flowsByCurrency(year));
    }

    /**
     * Retourne la répartition des virements par type d'opération.
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return les tranches par part décroissante, ou un tableau vide tant que
     *         {@code TRANSACTION_TYPE} n'est pas extraite
     */
    @GetMapping("/flows/by-type")
    @Operation(summary = "Répartition des flux par type d'opération",
            description = "Vide tant que TRANSACTION_TYPE n'a pas été extraite.")
    public ResponseEntity<List<FlowSliceDto>> flowsByType(
            @RequestParam(defaultValue = "0") int year) {
        return ResponseEntity.ok(analytics.flowsByType(year));
    }

    /**
     * Retourne l'activité croisée par mois et jour de semaine.
     *
     * <p>Ne dépend d'aucune colonne enrichie : la date métier est reconstruite
     * depuis l'identifiant T24 ({@code FTyyddd…}).</p>
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return les cellules renseignées ; les couples sans activité sont absents
     */
    @GetMapping("/flows/heatmap")
    @Operation(summary = "Heatmap d'activité mois × jour de semaine",
            description = "Fonctionne sans extraction enrichie : la date métier est "
                    + "reconstruite depuis l'identifiant T24 (FTyyddd…).")
    public ResponseEntity<List<HeatmapCellDto>> activityHeatmap(
            @RequestParam(defaultValue = "0") int year) {
        return ResponseEntity.ok(analytics.activityHeatmap(year));
    }

    /**
     * Retourne l'état des colonnes enrichies de la table des virements.
     *
     * <p>Pilote le panneau « à débloquer » du front, qui indique quelles
     * extractions relancer pour obtenir les indicateurs manquants.</p>
     *
     * @return une entrée par colonne attendue, {@code true} si elle est extraite
     */
    @GetMapping("/flows/columns-status")
    @Operation(summary = "État des colonnes enrichies de FUNDS_TRANSFER#HIS",
            description = "true = colonne extraite. Pilote le panneau « à débloquer » du front.")
    public ResponseEntity<Map<String, Boolean>> flowsColumnsStatus() {
        return ResponseEntity.ok(analytics.flowsColumnsStatus());
    }

    /**
     * Retourne les enseignements de la page Flux.
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return devise dominante, type d'opération dominant et montant moyen,
     *         chacun produit seulement si calculable
     */
    @GetMapping("/flows/insights")
    @Operation(summary = "Enseignements des flux",
            description = "Devise dominante, type d'opération dominant, ticket moyen.")
    public ResponseEntity<List<InsightDto>> flowsInsights(
            @RequestParam(defaultValue = "0") int year) {
        return ResponseEntity.ok(analytics.flowsInsights(year));
    }

    // ── Page Clients ──────────────────────────────────────────────────────────

    /**
     * Retourne le bandeau d'indicateurs de la page Clients.
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return démographie et acquisition ; les entrées en relation restent
     *         nulles tant que {@code DATE_AC_OPND} n'est pas extraite
     */
    @GetMapping("/customers/summary")
    @Operation(summary = "KPIs de la page Clients",
            description = "Démographie et acquisition. Les nouveaux clients restent à null "
                    + "tant que DATE_AC_OPND n'est pas extraite.")
    public ResponseEntity<CustomersSummaryDto> customersSummary(
            @RequestParam(defaultValue = "0") int year) {
        return ResponseEntity.ok(analytics.customersSummary(year));
    }

    /**
     * Retourne l'histogramme des âges par tranche de cinq ans.
     *
     * @return les tranches par âge croissant, la dernière regroupant les
     *         85 ans et plus
     */
    @GetMapping("/customers/age-histogram")
    @Operation(summary = "Histogramme des âges par pas de 5 ans")
    public ResponseEntity<List<BreakdownSliceDto>> ageHistogram() {
        return ResponseEntity.ok(analytics.ageHistogram());
    }

    /**
     * Retourne la répartition des clients par génération.
     *
     * @return les générations de la plus jeune à la plus ancienne, dérivées de
     *         l'année de naissance
     */
    @GetMapping("/customers/generations")
    @Operation(summary = "Répartition des clients par génération",
            description = "Dérivée de l'année de naissance (BIRTH_INCORP_DATE).")
    public ResponseEntity<List<BreakdownSliceDto>> generations() {
        return ResponseEntity.ok(analytics.generations());
    }

    /**
     * Retourne la série mensuelle des entrées en relation, comparée à N-1.
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return douze points, ou un tableau vide tant que {@code DATE_AC_OPND}
     *         n'est pas extraite
     */
    @GetMapping("/customers/new-monthly")
    @Operation(summary = "Nouveaux clients par mois, comparés à N-1",
            description = "Vide tant que DATE_AC_OPND n'a pas été extraite.")
    public ResponseEntity<List<MonthlyFlowDto>> newCustomersMonthly(
            @RequestParam(defaultValue = "0") int year) {
        return ResponseEntity.ok(analytics.newCustomersMonthly(year));
    }

    /**
     * Retourne la segmentation des clients selon une dimension.
     *
     * <p>Bornée aux douze valeurs les plus fréquentes ; les parts portent donc
     * sur ce sous-ensemble et non sur l'effectif total.</p>
     *
     * @param by dimension retenue : {@code sector}, {@code nationality},
     *           {@code gender}, {@code target} ou {@code status} ; vaut
     *           {@code sector} par défaut
     * @return les tranches par effectif décroissant, ou un tableau vide si la
     *         dimension est inconnue ou sa colonne non extraite
     */
    @GetMapping("/customers/breakdown")
    @Operation(summary = "Segmentation des clients selon une dimension",
            description = "Dimensions : sector, nationality, gender, target, status. "
                    + "Vide tant que la colonne correspondante n'est pas extraite.")
    public ResponseEntity<List<BreakdownSliceDto>> customersBreakdown(
            @Parameter(description = "Dimension : sector | nationality | gender | target | status")
            @RequestParam(defaultValue = "sector") String by) {
        return ResponseEntity.ok(analytics.customersBreakdown(by));
    }

    /**
     * Retourne les dimensions de segmentation client réellement exploitables.
     *
     * <p>Permet au front de ne proposer que les dimensions dont la colonne a
     * été extraite.</p>
     *
     * @return les clés de dimension disponibles, triées alphabétiquement
     */
    @GetMapping("/customers/dimensions")
    @Operation(summary = "Dimensions de segmentation dont la colonne est déjà extraite")
    public ResponseEntity<List<String>> customerDimensions() {
        return ResponseEntity.ok(analytics.availableCustomerDimensions());
    }

    /**
     * Retourne l'état des colonnes enrichies du référentiel clients.
     *
     * @return une entrée par colonne attendue, {@code true} si elle est extraite
     */
    @GetMapping("/customers/columns-status")
    @Operation(summary = "État des colonnes enrichies de CUSTOMER",
            description = "true = colonne extraite. Pilote le panneau « à débloquer » du front.")
    public ResponseEntity<Map<String, Boolean>> customersColumnsStatus() {
        return ResponseEntity.ok(analytics.customersColumnsStatus());
    }

    /**
     * Retourne les années proposables dans le filtre d'entrée en relation.
     *
     * @return les années par ordre décroissant, ou un tableau vide tant que
     *         {@code DATE_AC_OPND} n'est pas extraite
     */
    @GetMapping("/customers/acquisition-years")
    @Operation(summary = "Années d'entrée en relation disponibles",
            description = "Vide tant que DATE_AC_OPND n'a pas été extraite.")
    public ResponseEntity<List<Integer>> acquisitionYears() {
        return ResponseEntity.ok(analytics.acquisitionYears());
    }

    /**
     * Retourne les enseignements de la page Clients.
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return génération dominante, part des 25–54 ans et, selon les données,
     *         pic d'acquisition ou part des dossiers migrés
     */
    @GetMapping("/customers/insights")
    @Operation(summary = "Enseignements de la page Clients",
            description = "Génération dominante, cœur actif 25–54 ans, pic d'acquisition "
                    + "ou part des dossiers migrés.")
    public ResponseEntity<List<InsightDto>> customersInsights(
            @RequestParam(defaultValue = "0") int year) {
        return ResponseEntity.ok(analytics.customersInsights(year));
    }

    // ── Page Créances ─────────────────────────────────────────────────────────

    /**
     * Retourne le bandeau d'indicateurs de la page Créances.
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return facturation, encours, taux de recouvrement et délai de
     *         règlement ; les montants restent nuls tant que
     *         {@code OR_TOTAL_AMOUNT} et {@code OS_TOTAL_AMOUNT} ne sont pas
     *         extraites
     */
    @GetMapping("/billing/summary")
    @Operation(summary = "KPIs de la page Créances",
            description = "Facturation, encours, taux de recouvrement et délai de règlement. "
                    + "Les montants restent à null tant que OR/OS_TOTAL_AMOUNT ne sont pas extraites.")
    public ResponseEntity<BillingSummaryDto> billingSummary(
            @RequestParam(defaultValue = "0") int year) {
        return ResponseEntity.ok(analytics.billingSummary(year));
    }

    /**
     * Retourne la série mensuelle des factures, comparée à N-1.
     *
     * <p>Les volumes fonctionnent sans extraction enrichie : la date métier
     * est reconstruite depuis l'identifiant T24 ({@code AABILLyyddd…}).</p>
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return douze points, de janvier à décembre
     */
    @GetMapping("/billing/monthly")
    @Operation(summary = "Factures émises par mois, comparées à N-1",
            description = "Fonctionne sans extraction enrichie : la date métier est "
                    + "reconstruite depuis l'identifiant T24 (AABILLyyddd…).")
    public ResponseEntity<List<MonthlyFlowDto>> billingMonthly(
            @RequestParam(defaultValue = "0") int year) {
        return ResponseEntity.ok(analytics.billingMonthly(year));
    }

    /**
     * Retourne la segmentation des créances selon une dimension.
     *
     * @param by   dimension retenue : {@code aging}, {@code settle},
     *             {@code status}, {@code type} ou {@code method} ; vaut
     *             {@code aging} par défaut
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return les tranches par part décroissante, ou un tableau vide si la
     *         dimension est inconnue ou son satellite absent
     */
    @GetMapping("/billing/breakdown")
    @Operation(summary = "Segmentation des créances selon une dimension",
            description = "Dimensions : aging, settle, status, type, method. "
                    + "Vide tant que la colonne correspondante n'est pas extraite.")
    public ResponseEntity<List<FlowSliceDto>> billingBreakdown(
            @Parameter(description = "Dimension : aging | settle | status | type | method")
            @RequestParam(defaultValue = "aging") String by,
            @RequestParam(defaultValue = "0") int year) {
        return ResponseEntity.ok(analytics.billingBreakdown(by, year));
    }

    /**
     * Retourne les dimensions de segmentation des créances exploitables.
     *
     * @return les clés de dimension disponibles, triées alphabétiquement
     */
    @GetMapping("/billing/dimensions")
    @Operation(summary = "Dimensions de segmentation des créances déjà extraites")
    public ResponseEntity<List<String>> billingDimensions() {
        return ResponseEntity.ok(analytics.availableBillingDimensions());
    }

    /**
     * Retourne l'état des colonnes enrichies de la table de facturation.
     *
     * @return une entrée par champ attendu, {@code true} s'il est extrait
     */
    @GetMapping("/billing/columns-status")
    @Operation(summary = "État des colonnes enrichies de AA_BILL_DETAILS",
            description = "true = colonne extraite. Pilote le panneau « à débloquer » du front.")
    public ResponseEntity<Map<String, Boolean>> billingColumnsStatus() {
        return ResponseEntity.ok(analytics.billingColumnsStatus());
    }

    /**
     * Retourne les années proposables dans le filtre de facturation.
     *
     * @return les années par ordre décroissant
     */
    @GetMapping("/billing/years")
    @Operation(summary = "Années disponibles dans l'historique de facturation")
    public ResponseEntity<List<Integer>> billYears() {
        return ResponseEntity.ok(analytics.billYears());
    }

    /**
     * Retourne les enseignements de la page Créances.
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return mois record de facturation, part des factures réglées et délai
     *         moyen de règlement, chacun produit seulement si calculable
     */
    @GetMapping("/billing/insights")
    @Operation(summary = "Enseignements de la page Créances",
            description = "Mois record de facturation, part des factures réglées, "
                    + "délai moyen de règlement.")
    public ResponseEntity<List<InsightDto>> billingInsights(
            @RequestParam(defaultValue = "0") int year) {
        return ResponseEntity.ok(analytics.billingInsights(year));
    }
}
