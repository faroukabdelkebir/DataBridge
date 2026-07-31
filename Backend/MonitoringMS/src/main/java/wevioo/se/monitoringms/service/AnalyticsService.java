package wevioo.se.monitoringms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import wevioo.se.monitoringms.dto.analytics.*;
import wevioo.se.monitoringms.repository.AnalyticsRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Couche analytique métier alimentant les quatre pages du module : Synthèse,
 * Flux, Clients et Créances.
 *
 * <p>Chaque indicateur est, lorsque c'est possible, comparé à la même période
 * de l'année précédente, selon la logique des rapports « évolution vs A-1 »
 * attendus par les analystes.</p>
 *
 * <p><strong>Disponibilité progressive.</strong> Les indicateurs qui reposent
 * sur une colonne non encore extraite retournent {@code null} ou une liste
 * vide plutôt que d'échouer. Le {@code null} porte donc un sens précis :
 * « donnée indisponible », à distinguer de la valeur zéro.</p>
 *
 * <p><strong>Arrondis.</strong> Les pourcentages et montants moyens sont
 * arrondis au dixième, pour éviter d'afficher une précision que la qualité
 * des données ne justifie pas.</p>
 *
 * <p><strong>Mise en cache.</strong> Toutes les méthodes coûteuses sont
 * mises en cache cinq minutes, la clé intégrant les paramètres de filtrage.
 * Les appels internes entre méthodes de ce service contournent le cache,
 * n'étant pas interceptés par le mandataire Spring.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    /**
     * Nom du cache des indicateurs analytiques, déclaré dans
     * {@link wevioo.se.monitoringms.config.CacheConfig} avec une rétention de
     * cinq minutes.
     */
    public static final String CACHE_ANALYTICS = "analytics";

    /** Accès aux requêtes analytiques sur les tables extraites de T24. */
    private final AnalyticsRepository repo;

    // ── Bandeau KPI (page Synthèse) ──────────────────────────────────────────

    /**
     * Calcule le bandeau d'indicateurs de la page Synthèse.
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique, auquel
     *             cas aucune comparaison à l'année précédente n'est tentée
     * @return les indicateurs de synthèse
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'summary-' + #year")
    public AnalyticsSummaryDto summary(int year) {
        long ftN  = repo.countTransfers(year);
        long ftN1 = year > 0 ? repo.countTransfers(year - 1) : 0;
        long biN  = repo.countBills(year);
        long biN1 = year > 0 ? repo.countBills(year - 1) : 0;

        return AnalyticsSummaryDto.builder()
                .totalClients(repo.countClients())
                .ageMoyen(repo.averageAge())
                .nbGestionnaires(repo.countOfficers())
                .nbVirements(ftN)
                .deltaVirementsPct(delta(ftN, ftN1))
                .nbFactures(biN)
                .deltaFacturesPct(delta(biN, biN1))
                .montantVirements(repo.sumTransfers(year))
                .devisePrincipale(dominantCurrency(year))
                .build();
    }

    // ── Page Flux : KPIs ─────────────────────────────────────────────────────

    /**
     * Calcule le bandeau d'indicateurs de la page Flux.
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return les indicateurs de flux ; montants et devises restent à
     *         {@code null} tant que les colonnes correspondantes ne sont pas
     *         extraites
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'flows-summary-' + #year")
    public FlowsSummaryDto flowsSummary(int year) {
        long   nbN      = repo.countTransfers(year);
        long   nbN1     = year > 0 ? repo.countTransfers(year - 1) : 0;
        Double amountN  = repo.sumTransfers(year);
        Double amountN1 = year > 0 ? repo.sumTransfers(year - 1) : null;

        List<Map<String, Object>> currencies = repo.flowsByCurrency(year);
        List<Map<String, Object>> types      = repo.flowsByType(year);

        return FlowsSummaryDto.builder()
                .nbOperations(nbN)
                .deltaOpsPct(delta(nbN, nbN1))
                .montantTotal(amountN)
                .deltaMontantPct(deltaAmount(amountN, amountN1))
                .ticketMoyen(amountN != null && nbN > 0 ? round1(amountN / nbN) : null)
                .deviseDominante(currencies.isEmpty()
                        ? null : String.valueOf(currencies.get(0).get("label")))
                .nbDevises(currencies.isEmpty() ? null : currencies.size())
                .nbTypes(types.isEmpty() ? null : types.size())
                .build();
    }

    /**
     * Dresse l'état des colonnes enrichies de la page Flux.
     *
     * <p>Pilote le panneau « à débloquer » du front, qui indique à
     * l'utilisateur quelles extractions relancer pour obtenir les indicateurs
     * manquants.</p>
     *
     * @return une entrée par colonne attendue, {@code true} si elle est extraite
     */
    public Map<String, Boolean> flowsColumnsStatus() {
        return repo.ftEnrichedColumnsStatus();
    }

    // ── Évolution mensuelle N vs N-1 (volumes + montants) ────────────────────

    /**
     * Construit la série mensuelle des virements, comparée à l'année précédente.
     *
     * <p>La série compte toujours douze points : les mois sans opération sont
     * remplis à zéro, ce qui évite au front d'avoir à combler les trous.</p>
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique, auquel
     *             cas les valeurs de l'année précédente restent à {@code null}
     * @return les douze points de la série, de janvier à décembre
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'monthly-' + #year")
    public List<MonthlyFlowDto> monthlyTransfers(int year) {
        Map<Integer, Long>   current    = indexByMonth(repo.monthlyTransfers(year));
        Map<Integer, Long>   previous   = year > 0
                ? indexByMonth(repo.monthlyTransfers(year - 1)) : Map.of();
        Map<Integer, Double> amounts    = indexAmounts(repo.monthlyAmounts(year));
        Map<Integer, Double> amountsN1  = year > 0
                ? indexAmounts(repo.monthlyAmounts(year - 1)) : Map.of();

        List<MonthlyFlowDto> out = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            out.add(MonthlyFlowDto.builder()
                    .mois(m)
                    .nbOperations(current.getOrDefault(m, 0L))
                    .nbOperationsN1(previous.isEmpty() ? null : previous.getOrDefault(m, 0L))
                    .montantTotal(amounts.isEmpty() ? null : amounts.getOrDefault(m, 0.0))
                    .montantTotalN1(amountsN1.isEmpty() ? null : amountsN1.getOrDefault(m, 0.0))
                    .build());
        }
        return out;
    }

    // ── Page Flux : répartitions ─────────────────────────────────────────────

    /**
     * Répartit les virements par devise de débit.
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return les tranches triées par part décroissante ; liste vide tant que
     *         {@code DEBIT_CURRENCY} n'est pas extraite
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'flows-currency-' + #year")
    public List<FlowSliceDto> flowsByCurrency(int year) {
        return toFlowSlices(repo.flowsByCurrency(year));
    }

    /**
     * Répartit les virements par type d'opération.
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return les tranches triées par part décroissante ; liste vide tant que
     *         {@code TRANSACTION_TYPE} n'est pas extraite
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'flows-type-' + #year")
    public List<FlowSliceDto> flowsByType(int year) {
        return toFlowSlices(repo.flowsByType(year));
    }

    /**
     * Croise l'activité par mois et par jour de semaine.
     *
     * <p>Ne dépend d'aucune colonne enrichie : la date métier est reconstruite
     * depuis l'identifiant T24.</p>
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return les cellules renseignées ; les couples sans activité sont absents
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'flows-heatmap-' + #year")
    public List<HeatmapCellDto> activityHeatmap(int year) {
        return repo.activityHeatmap(year).stream()
                .map(r -> HeatmapCellDto.builder()
                        .mois(((Number) r.get("mois")).intValue())
                        .jourSemaine(((Number) r.get("jour")).intValue())
                        .nb(((Number) r.get("nb")).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    // ── Page Flux : insights automatiques ────────────────────────────────────

    /**
     * Produit les enseignements de la page Flux : devise dominante, type
     * d'opération dominant et montant moyen par opération.
     *
     * <p>Chaque enseignement n'apparaît que si la colonne qui le rend
     * calculable a été extraite. Les tranches portant le libellé de
     * remplacement {@code —} sont écartées : elles signalent une valeur
     * manquante, non une catégorie réelle.</p>
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return les enseignements disponibles, éventuellement aucun
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'flows-insights-' + #year")
    public List<InsightDto> flowsInsights(int year) {
        List<InsightDto> out = new ArrayList<>();

        List<FlowSliceDto> currencies = flowsByCurrency(year).stream()
                .filter(s -> !"\u2014".equals(s.getLabel())).collect(Collectors.toList());
        if (!currencies.isEmpty()) {
            FlowSliceDto lead = currencies.get(0);
            out.add(InsightDto.builder()
                    .type("LEADER")
                    .titleKey("analytics.flows.insight.currency")
                    .value(lead.getLabel())
                    .detailKey("analytics.flows.insight.currencyDetail")
                    .params(Map.of("pct", lead.getPct(), "count", lead.getNbOperations()))
                    .build());
        }

        List<FlowSliceDto> types = flowsByType(year).stream()
                .filter(s -> !"\u2014".equals(s.getLabel())).collect(Collectors.toList());
        if (!types.isEmpty()) {
            FlowSliceDto lead = types.get(0);
            out.add(InsightDto.builder()
                    .type("CONCENTRATION")
                    .titleKey("analytics.flows.insight.type")
                    .value(lead.getLabel())
                    .detailKey("analytics.flows.insight.typeDetail")
                    .params(Map.of("pct", lead.getPct()))
                    .build());
        }

        FlowsSummaryDto s = flowsSummary(year);
        if (s.getTicketMoyen() != null) {
            out.add(InsightDto.builder()
                    .type("PIC_ACTIVITE")
                    .titleKey("analytics.flows.insight.ticket")
                    .value(String.format("%,.0f", s.getTicketMoyen()))
                    .detail(s.getDeviseDominante() == null ? "" : s.getDeviseDominante())
                    .build());
        }
        return out;
    }

    // ── Page Clients : KPIs ──────────────────────────────────────────────────

    /**
     * Calcule le bandeau d'indicateurs de la page Clients.
     *
     * <p>La comparaison des entrées en relation à l'année précédente n'est
     * tentée que si la donnée est disponible pour l'année courante.</p>
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return les indicateurs clients
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'customers-summary-' + #year")
    public CustomersSummaryDto customersSummary(int year) {
        Long newN  = repo.countNewCustomers(year);
        Long newN1 = (newN != null && year > 0) ? repo.countNewCustomers(year - 1) : null;

        List<Map<String, Object>> genders       = repo.customerBreakdown("gender", 10);
        List<Map<String, Object>> nationalities = repo.customerBreakdown("nationality", 500);
        List<Map<String, Object>> sectors       = repo.customerBreakdown("sector", 500);

        return CustomersSummaryDto.builder()
                .totalClients(repo.countClients())
                .ageMoyen(round1OrNull(repo.averageAge()))
                .nbGestionnaires(repo.countOfficers())
                .pctMigration(round1OrNull(repo.migrationShare()))
                .nouveauxClients(newN)
                .deltaNouveauxPct(newN != null && newN1 != null ? delta(newN, newN1) : null)
                .pctFemmes(femaleShare(genders))
                .nbNationalites(nationalities.isEmpty() ? null : nationalities.size())
                .nbSecteurs(sectors.isEmpty() ? null : sectors.size())
                .build();
    }

    /**
     * Dresse l'état des colonnes enrichies de la page Clients.
     *
     * @return une entrée par colonne attendue, {@code true} si elle est extraite
     */
    public Map<String, Boolean> customersColumnsStatus() {
        return repo.customerEnrichedColumnsStatus();
    }

    /**
     * Liste les années d'entrée en relation proposables en filtre.
     *
     * @return les années par ordre décroissant ; liste vide tant que la date
     *         d'ouverture de compte n'est pas extraite
     */
    public List<Integer> acquisitionYears() {
        return repo.acquisitionYears();
    }

    // ── Page Clients : démographie ───────────────────────────────────────────

    /**
     * Construit l'histogramme des âges par tranche de cinq ans.
     *
     * <p>Les libellés sont formés à partir de la borne basse renvoyée par la
     * base : {@code 40–44} pour une tranche standard, {@code 85 +} pour la
     * dernière, qui regroupe les âges élevés.</p>
     *
     * @return les tranches par âge croissant ; liste vide si aucun âge n'est
     *         exploitable
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'customers-histogram'")
    public List<BreakdownSliceDto> ageHistogram() {
        List<Map<String, Object>> rows = repo.ageHistogram();
        long total = rows.stream().mapToLong(r -> ((Number) r.get("nb")).longValue()).sum();
        return rows.stream()
                .map(r -> {
                    int band = ((Number) r.get("band")).intValue();
                    String label = band >= 85 ? "85 +" : band + "–" + (band + 4);
                    return slice(label, ((Number) r.get("nb")).longValue(), total);
                })
                .collect(Collectors.toList());
    }

    /**
     * Répartit les clients par génération, dérivée de l'année de naissance.
     *
     * @return les générations de la plus jeune à la plus ancienne ; liste vide
     *         si aucune année de naissance n'est exploitable
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'customers-generations'")
    public List<BreakdownSliceDto> generations() {
        List<Map<String, Object>> rows = repo.generations();
        long total = rows.stream().mapToLong(r -> ((Number) r.get("nb")).longValue()).sum();
        return rows.stream()
                .map(r -> slice(String.valueOf(r.get("label")),
                        ((Number) r.get("nb")).longValue(), total))
                .collect(Collectors.toList());
    }

    /**
     * Construit la série mensuelle des entrées en relation.
     *
     * <p>Contrairement aux autres séries, celle-ci retourne une liste vide
     * plutôt que douze points à zéro lorsque la donnée est indisponible :
     * l'appelant peut ainsi distinguer « colonne non extraite » de « aucune
     * entrée en relation ».</p>
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return les douze points de la série, ou une liste vide tant que la date
     *         d'ouverture de compte n'est pas extraite
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'customers-new-monthly-' + #year")
    public List<MonthlyFlowDto> newCustomersMonthly(int year) {
        List<Map<String, Object>> raw = repo.newCustomersMonthly(year);
        if (raw.isEmpty()) return List.of();

        Map<Integer, Long> current  = indexByMonth(raw);
        Map<Integer, Long> previous = year > 0
                ? indexByMonth(repo.newCustomersMonthly(year - 1)) : Map.of();

        List<MonthlyFlowDto> out = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            out.add(MonthlyFlowDto.builder()
                    .mois(m)
                    .nbOperations(current.getOrDefault(m, 0L))
                    .nbOperationsN1(previous.isEmpty() ? null : previous.getOrDefault(m, 0L))
                    .montantTotal(null)
                    .montantTotalN1(null)
                    .build());
        }
        return out;
    }

    /**
     * Segmente les clients selon une dimension autorisée.
     *
     * <p>La segmentation est bornée aux douze valeurs les plus fréquentes ;
     * les parts sont donc calculées sur ce sous-ensemble et non sur
     * l'effectif total.</p>
     *
     * @param dimension clé de dimension : {@code sector}, {@code nationality},
     *                  {@code gender}, {@code target} ou {@code status}
     * @return les tranches par effectif décroissant ; liste vide si la
     *         dimension est inconnue ou sa colonne non extraite
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'customers-breakdown-' + #dimension")
    public List<BreakdownSliceDto> customersBreakdown(String dimension) {
        List<Map<String, Object>> rows = repo.customerBreakdown(dimension, 12);
        long total = rows.stream().mapToLong(r -> ((Number) r.get("nb")).longValue()).sum();
        return rows.stream()
                .map(r -> slice(String.valueOf(r.get("label")),
                        ((Number) r.get("nb")).longValue(), total))
                .collect(Collectors.toList());
    }

    /**
     * Liste les dimensions de segmentation client réellement exploitables.
     *
     * <p>Permet au front de ne proposer que les dimensions dont la colonne a
     * été extraite.</p>
     *
     * @return les clés de dimension disponibles, triées alphabétiquement
     */
    public List<String> availableCustomerDimensions() {
        return AnalyticsRepository.CUSTOMER_DIMENSIONS.entrySet().stream()
                .filter(e -> repo.hasColumn(AnalyticsRepository.CUS_TABLE, e.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
    }

    // ── Page Clients : insights automatiques ────────────────────────────────

    /**
     * Produit les enseignements de la page Clients.
     *
     * <p>Trois enseignements au plus : la génération dominante, la part du
     * cœur actif — les 25 à 54 ans — et, selon les données disponibles, le
     * pic d'acquisition ou, à défaut, la part des dossiers issus de la
     * migration initiale.</p>
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return les enseignements disponibles, éventuellement aucun
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'customers-insights-' + #year")
    public List<InsightDto> customersInsights(int year) {
        List<InsightDto> out = new ArrayList<>();

        generations().stream()
                .max((a, b) -> Long.compare(a.getValue(), b.getValue()))
                .ifPresent(top -> out.add(InsightDto.builder()
                        .type("LEADER")
                        .titleKey("analytics.customers.insight.generation")
                        .value(top.getLabel())
                        .detailKey("analytics.customers.insight.generationDetail")
                        .params(Map.of("count", top.getValue(), "pct", top.getPct()))
                        .build()));

        Double core = repo.ageShare(25, 54);
        if (core != null) {
            out.add(InsightDto.builder()
                    .type("CONCENTRATION")
                    .titleKey("analytics.customers.insight.core")
                    .value(round1(core) + " %")
                    .detailKey("analytics.customers.insight.coreDetail")
                    .build());
        }

        // Pic d'acquisition si l'entrée en relation est extraite, sinon part migrée.
        List<MonthlyFlowDto> acquisition = newCustomersMonthly(year);
        if (!acquisition.isEmpty()) {
            acquisition.stream()
                    .max((a, b) -> Long.compare(a.getNbOperations(), b.getNbOperations()))
                    .ifPresent(peak -> out.add(InsightDto.builder()
                            .type("PIC_ACTIVITE")
                            .titleKey("analytics.customers.insight.peak")
                            .value(String.format("%02d/%s", peak.getMois(),
                                    year > 0 ? year : "—"))
                            .detailKey("analytics.customers.insight.peakDetail")
                            .params(Map.of("count", peak.getNbOperations()))
                            .build()));
        } else {
            Double migration = repo.migrationShare();
            if (migration != null) {
                out.add(InsightDto.builder()
                        .type("PIC_ACTIVITE")
                        .titleKey("analytics.customers.insight.migration")
                        .value(round1(migration) + " %")
                        .detailKey("analytics.customers.insight.migrationDetail")
                        .build());
            }
        }
        return out;
    }

    // ── Page Créances : KPIs ─────────────────────────────────────────────────

    /**
     * Calcule le bandeau d'indicateurs de la page Créances.
     *
     * <p>Le taux de recouvrement n'est produit que si les deux montants sont
     * disponibles et que le montant facturé est strictement positif.</p>
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return les indicateurs de facturation
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'billing-summary-' + #year")
    public BillingSummaryDto billingSummary(int year) {
        long   nbN      = repo.countBills(year);
        long   nbN1     = year > 0 ? repo.countBills(year - 1) : 0;
        Double billed   = repo.billedAmount(year);
        Double pending  = repo.outstandingAmount(year);

        Double recovery = null;
        if (billed != null && pending != null && billed > 0) {
            recovery = round1(100.0 * (billed - pending) / billed);
        }

        return BillingSummaryDto.builder()
                .nbFactures(nbN)
                .deltaFacturesPct(delta(nbN, nbN1))
                .montantFacture(billed)
                .encoursTotal(pending)
                .tauxRecouvrement(recovery)
                .pctPayees(paidShare(year))
                .delaiMoyenReglement(round1OrNull(repo.avgSettlementDelay(year)))
                .build();
    }

    /**
     * Dresse l'état des colonnes enrichies de la page Créances.
     *
     * @return une entrée par champ attendu, {@code true} s'il est extrait
     */
    public Map<String, Boolean> billingColumnsStatus() {
        return repo.billingEnrichedColumnsStatus();
    }

    /**
     * Liste les années de facturation proposables en filtre.
     *
     * @return les années par ordre décroissant
     */
    public List<Integer> billYears() {
        return repo.billYears();
    }

    // ── Page Créances : évolution & segmentation ─────────────────────────────

    /**
     * Construit la série mensuelle des factures, comparée à l'année précédente.
     *
     * <p>Les volumes sont toujours disponibles, la date d'émission étant
     * reconstruite depuis l'identifiant T24 ; les montants ne le sont que si
     * la colonne correspondante est extraite.</p>
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return les douze points de la série, de janvier à décembre
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'billing-monthly-' + #year")
    public List<MonthlyFlowDto> billingMonthly(int year) {
        Map<Integer, Long>   current   = indexByMonth(repo.monthlyBills(year));
        Map<Integer, Long>   previous  = year > 0
                ? indexByMonth(repo.monthlyBills(year - 1)) : Map.of();
        Map<Integer, Double> amounts   = indexAmounts(repo.monthlyBillAmounts(year));
        Map<Integer, Double> amountsN1 = year > 0
                ? indexAmounts(repo.monthlyBillAmounts(year - 1)) : Map.of();

        List<MonthlyFlowDto> out = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            out.add(MonthlyFlowDto.builder()
                    .mois(m)
                    .nbOperations(current.getOrDefault(m, 0L))
                    .nbOperationsN1(previous.isEmpty() ? null : previous.getOrDefault(m, 0L))
                    .montantTotal(amounts.isEmpty() ? null : amounts.getOrDefault(m, 0.0))
                    .montantTotalN1(amountsN1.isEmpty() ? null : amountsN1.getOrDefault(m, 0.0))
                    .build());
        }
        return out;
    }

    /**
     * Segmente les créances selon une dimension autorisée.
     *
     * <p>Bornée aux douze valeurs les plus fréquentes.</p>
     *
     * @param dimension clé de dimension : {@code aging}, {@code settle},
     *                  {@code status}, {@code type} ou {@code method}
     * @param year      année à analyser ; {@code 0} pour tout l'historique
     * @return les tranches triées par part décroissante ; liste vide si la
     *         dimension est inconnue ou son satellite absent
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'billing-breakdown-' + #dimension + '-' + #year")
    public List<FlowSliceDto> billingBreakdown(String dimension, int year) {
        return toFlowSlices(repo.billingBreakdown(dimension, year, 12));
    }

    /**
     * Liste les dimensions de segmentation des créances réellement exploitables.
     *
     * @return les clés de dimension disponibles, triées alphabétiquement
     */
    public List<String> availableBillingDimensions() {
        return AnalyticsRepository.BILLING_DIMENSIONS.entrySet().stream()
                .filter(e -> repo.hasColumn(AnalyticsRepository.BILL_TABLE, e.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
    }

    // ── Page Créances : insights automatiques ────────────────────────────────

    /**
     * Produit les enseignements de la page Créances.
     *
     * <p>Le mois record de facturation est calculable sans extraction
     * enrichie ; la part des factures réglées et le délai moyen de règlement
     * n'apparaissent qu'après enrichissement.</p>
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return les enseignements disponibles, éventuellement aucun
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'billing-insights-' + #year")
    public List<InsightDto> billingInsights(int year) {
        List<InsightDto> out = new ArrayList<>();

        repo.monthlyBills(year).stream()
                .max((a, b) -> Long.compare(((Number) a.get("nb")).longValue(),
                        ((Number) b.get("nb")).longValue()))
                .ifPresent(peak -> out.add(InsightDto.builder()
                        .type("LEADER")
                        .titleKey("analytics.billing.insight.peak")
                        .value(String.format("%02d/%s", ((Number) peak.get("mois")).intValue(),
                                year > 0 ? year : "—"))
                        .detailKey("analytics.billing.insight.peakDetail")
                        .params(Map.of("count", peak.get("nb")))
                        .build()));

        Double paid = paidShare(year);
        if (paid != null) {
            out.add(InsightDto.builder()
                    .type("CONCENTRATION")
                    .titleKey("analytics.billing.insight.paid")
                    .value(paid + " %")
                    .detailKey("analytics.billing.insight.paidDetail")
                    .build());
        }

        Double delay = repo.avgSettlementDelay(year);
        if (delay != null) {
            out.add(InsightDto.builder()
                    .type("PIC_ACTIVITE")
                    .titleKey("analytics.billing.insight.delay")
                    .value(round1(Math.abs(delay)) + " j")
                    .detailKey(delay >= 0 ? "analytics.billing.insight.delayAfter"
                            : "analytics.billing.insight.delayBefore")
                    .build());
        }
        return out;
    }

    /**
     * Calcule la part des factures réglées.
     *
     * <p>Les statuts sont comparés par mot-clé, T24 employant plusieurs
     * variantes ({@code PAID}, {@code REPAID}, {@code SETTLED},
     * {@code PART.SETTLED}). Les formes négatives — {@code UNPAID},
     * {@code UNSETTLED} — sont écartées en premier : elles contiennent les
     * mêmes mots-clés et seraient sinon comptées comme des règlements.</p>
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return la part en pourcentage, ou {@code null} tant que
     *         {@code SETTLE_STATUS} n'est pas extrait
     */
    private Double paidShare(int year) {
        List<Map<String, Object>> rows = repo.billingBreakdown("settle", year, 50);
        if (rows.isEmpty()) return null;
        long total = rows.stream().mapToLong(r -> ((Number) r.get("nb")).longValue()).sum();
        long paid = rows.stream()
                .filter(r -> {
                    String label = String.valueOf(r.get("label")).toUpperCase();
                    // UNPAID / UNSETTLED contiennent PAID / SETTLED : à exclure d'abord
                    if (label.startsWith("UN") || label.contains("NOT")) return false;
                    // REPAID / PAID / SETTLED / PART.SETTLED comptent comme réglées
                    return label.contains("PAID") || label.contains("SETTLED");
                })
                .mapToLong(r -> ((Number) r.get("nb")).longValue()).sum();
        return total == 0 ? null : round1(100.0 * paid / total);
    }

    // ── Répartitions (page Synthèse) ─────────────────────────────────────────

    /**
     * Répartit les clients en six tranches d'âge.
     *
     * @return les tranches de la plus jeune à la plus âgée ; liste vide si
     *         aucun âge n'est exploitable
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'pyramid'")
    public List<BreakdownSliceDto> agePyramid() {
        List<Map<String, Object>> rows = repo.agePyramid();
        long total = rows.stream().mapToLong(r -> ((Number) r.get("nb")).longValue()).sum();
        return rows.stream()
                .map(r -> slice((String) r.get("tranche"), ((Number) r.get("nb")).longValue(), total))
                .collect(Collectors.toList());
    }

    /**
     * Classe les gestionnaires par taille de portefeuille.
     *
     * @param top nombre de gestionnaires à retourner
     * @return les gestionnaires du plus gros portefeuille au plus petit
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'officers-' + #top")
    public List<OfficerPerfDto> topOfficers(int top) {
        return repo.portfolioByOfficer(top).stream()
                .map(r -> OfficerPerfDto.builder()
                        .officer(String.valueOf(r.get("officer")))
                        .nbClients(((Number) r.get("nb")).longValue())
                        .ageMoyen(r.get("age_moyen") == null ? null : ((Number) r.get("age_moyen")).doubleValue())
                        .partPct(round1(((Number) r.get("part_pct")).doubleValue()))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Répartit les créances par état de vieillissement, sans filtre d'année.
     *
     * @return les tranches par effectif décroissant ; liste vide tant que
     *         {@code AGING_STATUS} n'est pas extrait
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'aging'")
    public List<BreakdownSliceDto> billingAging() {
        List<Map<String, Object>> rows = repo.billingAging();
        long total = rows.stream().mapToLong(r -> ((Number) r.get("nb")).longValue()).sum();
        return rows.stream()
                .map(r -> slice(String.valueOf(r.get("label")), ((Number) r.get("nb")).longValue(), total))
                .collect(Collectors.toList());
    }

    /**
     * Liste les années de virement proposables en filtre.
     *
     * @return les années par ordre décroissant
     */
    public List<Integer> availableYears() {
        return repo.transferYears();
    }

    // ── Insights (page Synthèse) ─────────────────────────────────────────────

    /**
     * Produit les enseignements de la page Synthèse.
     *
     * <p>Gestionnaire en tête, concentration du portefeuille sur les cinq
     * premiers et mois de pic d'activité. Les deux premiers ne sont produits
     * qu'en présence d'au moins un gestionnaire.</p>
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return les enseignements disponibles, éventuellement aucun
     */
    @Cacheable(value = CACHE_ANALYTICS, key = "'insights-' + #year")
    public List<InsightDto> insights(int year) {
        List<InsightDto> out = new ArrayList<>();

        List<OfficerPerfDto> officers = topOfficers(5);
        if (!officers.isEmpty()) {
            OfficerPerfDto leader = officers.get(0);
            out.add(InsightDto.builder()
                    .type("LEADER")
                    .titleKey("analytics.insight.leader")
                    .value(leader.getOfficer())
                    .detailKey("analytics.insight.leaderDetail")
                    .params(Map.of("count", leader.getNbClients(), "pct", leader.getPartPct()))
                    .build());

            double top5 = round1(officers.stream().mapToDouble(OfficerPerfDto::getPartPct).sum());
            out.add(InsightDto.builder()
                    .type("CONCENTRATION")
                    .titleKey("analytics.insight.concentration")
                    .value(top5 + " %")
                    .detailKey("analytics.insight.concentrationDetail")
                    .params(Map.of("count", repo.countOfficers()))
                    .build());
        }

        repo.monthlyTransfers(year).stream()
                .max((a, b) -> Long.compare(((Number) a.get("nb")).longValue(),
                        ((Number) b.get("nb")).longValue()))
                .ifPresent(peak -> out.add(InsightDto.builder()
                        .type("PIC_ACTIVITE")
                        .titleKey("analytics.insight.peak")
                        .value(String.format("%02d/%s", ((Number) peak.get("mois")).intValue(),
                                year > 0 ? year : "—"))
                        .detailKey("analytics.insight.peakDetail")
                        .params(Map.of("count", peak.get("nb")))
                        .build()));

        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Identifie la devise la plus utilisée.
     *
     * @param year année à analyser ; {@code 0} pour tout l'historique
     * @return le code devise, ou {@code null} tant que {@code DEBIT_CURRENCY}
     *         n'est pas extraite
     */
    private String dominantCurrency(int year) {
        List<Map<String, Object>> currencies = repo.flowsByCurrency(year);
        return currencies.isEmpty() ? null : String.valueOf(currencies.get(0).get("label"));
    }

    /**
     * Convertit des lignes brutes en tranches de répartition.
     *
     * <p>La part est calculée sur les montants lorsqu'ils sont présents et que
     * leur total est strictement positif. Sinon elle retombe sur le volume :
     * un encours intégralement soldé donnerait zéro partout, ce qui
     * n'apprendrait rien.</p>
     *
     * @param rows lignes portant les clés {@code label}, {@code nb} et
     *             éventuellement {@code montant}
     * @return les tranches triées par part décroissante
     */
    private List<FlowSliceDto> toFlowSlices(List<Map<String, Object>> rows) {
        boolean hasAmounts = rows.stream().anyMatch(r -> r.get("montant") != null);
        double amountTotal = rows.stream().filter(r -> r.get("montant") != null)
                .mapToDouble(r -> ((Number) r.get("montant")).doubleValue()).sum();
        boolean pctOnAmounts = hasAmounts && amountTotal > 0;
        double total = pctOnAmounts
                ? amountTotal
                : rows.stream().mapToLong(r -> ((Number) r.get("nb")).longValue()).sum();

        return rows.stream()
                .map(r -> {
                    long   nb     = ((Number) r.get("nb")).longValue();
                    Double amount = r.get("montant") == null
                            ? null : ((Number) r.get("montant")).doubleValue();
                    double base   = pctOnAmounts ? (amount == null ? 0 : amount) : nb;
                    return FlowSliceDto.builder()
                            .label(String.valueOf(r.get("label")))
                            .nbOperations(nb)
                            .montantTotal(amount)
                            .ticketMoyen(amount != null && nb > 0 ? round1(amount / nb) : null)
                            .pct(total == 0 ? 0 : round1(100.0 * base / total))
                            .build();
                })
                .sorted((a, b) -> Double.compare(b.getPct(), a.getPct()))
                .collect(Collectors.toList());
    }

    /**
     * Indexe des effectifs mensuels par numéro de mois.
     *
     * @param rows lignes portant les clés {@code mois} et {@code nb}
     * @return la correspondance mois → effectif ; en cas de doublon, la
     *         première occurrence est conservée
     */
    private static Map<Integer, Long> indexByMonth(List<Map<String, Object>> rows) {
        return rows.stream().collect(Collectors.toMap(
                r -> ((Number) r.get("mois")).intValue(),
                r -> ((Number) r.get("nb")).longValue(),
                (a, b) -> a));
    }

    /**
     * Indexe des montants mensuels par numéro de mois.
     *
     * <p>Les lignes sans montant sont écartées, une carte vide signalant alors
     * à l'appelant que les montants ne sont pas disponibles.</p>
     *
     * @param rows lignes portant les clés {@code mois} et {@code montant}
     * @return la correspondance mois → montant
     */
    private static Map<Integer, Double> indexAmounts(List<Map<String, Object>> rows) {
        return rows.stream()
                .filter(r -> r.get("montant") != null)
                .collect(Collectors.toMap(
                        r -> ((Number) r.get("mois")).intValue(),
                        r -> ((Number) r.get("montant")).doubleValue(),
                        (a, b) -> a));
    }

    /**
     * Calcule l'évolution relative entre deux effectifs.
     *
     * @param current  valeur de la période courante
     * @param previous valeur de la période précédente
     * @return l'évolution en pourcentage arrondie au dixième, ou {@code null}
     *         si la période précédente est vide — le rapport serait alors
     *         indéfini
     */
    private static Double delta(long current, long previous) {
        if (previous <= 0) return null;
        return round1(100.0 * (current - previous) / previous);
    }

    /**
     * Arrondit une valeur au dixième en préservant l'absence de valeur.
     *
     * @param v valeur à arrondir, éventuellement {@code null}
     * @return la valeur arrondie, ou {@code null} si l'entrée l'était
     */
    private static Double round1OrNull(Double v) {
        return v == null ? null : round1(v);
    }

    /**
     * Calcule la part des clientes à partir de la répartition par genre.
     *
     * <p>Sont comptées comme féminines les valeurs commençant par {@code F},
     * T24 employant indifféremment {@code F} et {@code FEMALE}.</p>
     *
     * @param genderRows lignes portant les clés {@code label} et {@code nb}
     * @return la part en pourcentage, ou {@code null} si la répartition est
     *         vide ou son effectif total nul
     */
    private static Double femaleShare(List<Map<String, Object>> genderRows) {
        if (genderRows.isEmpty()) return null;
        long total = genderRows.stream()
                .mapToLong(r -> ((Number) r.get("nb")).longValue()).sum();
        long female = genderRows.stream()
                .filter(r -> String.valueOf(r.get("label")).toUpperCase().startsWith("F"))
                .mapToLong(r -> ((Number) r.get("nb")).longValue()).sum();
        return total == 0 ? null : round1(100.0 * female / total);
    }

    /**
     * Calcule l'évolution relative entre deux montants.
     *
     * <p>Pendant de {@link #delta(long, long)} pour des valeurs nullables :
     * un montant absent d'un côté rend la comparaison impossible.</p>
     *
     * @param current  montant de la période courante
     * @param previous montant de la période précédente
     * @return l'évolution en pourcentage arrondie au dixième, ou {@code null}
     *         si l'un des montants manque ou si le précédent n'est pas positif
     */
    private static Double deltaAmount(Double current, Double previous) {
        if (current == null || previous == null || previous <= 0) return null;
        return round1(100.0 * (current - previous) / previous);
    }

    /**
     * Construit une tranche de répartition en calculant sa part.
     *
     * @param label libellé de la tranche
     * @param value effectif de la tranche
     * @param total effectif total servant de base au calcul
     * @return la tranche, de part nulle si le total l'est
     */
    private static BreakdownSliceDto slice(String label, long value, long total) {
        return BreakdownSliceDto.builder()
                .label(label)
                .value(value)
                .pct(total == 0 ? 0 : round1(100.0 * value / total))
                .build();
    }

    /**
     * Arrondit une valeur au dixième.
     *
     * <p>Arrondi appliqué uniformément aux pourcentages et montants moyens du
     * service, la qualité des données ne justifiant pas davantage de
     * précision.</p>
     *
     * @param v valeur à arrondir
     * @return la valeur arrondie au dixième
     */
    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
