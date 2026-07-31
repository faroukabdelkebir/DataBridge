package wevioo.se.monitoringms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wevioo.se.monitoringms.dto.analytics.*;
import wevioo.se.monitoringms.repository.AnalyticsRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Couvre les règles de calcul analytiques : évolutions vs N-1, parts en %,
 * remplissage des 12 mois et dégradation propre quand une colonne enrichie
 * n'a pas encore été extraite (valeurs à null plutôt qu'exception).
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private AnalyticsRepository repo;

    private AnalyticsService analytics;

    @BeforeEach
    void setUp() {
        analytics = new AnalyticsService(repo);
    }

    // ── Synthèse ─────────────────────────────────────────────────────────────

    @Nested
    class Summary {

        @Test
        void computesEvolutionAgainstPreviousYear() {
            when(repo.countTransfers(2025)).thenReturn(120L);
            when(repo.countTransfers(2024)).thenReturn(100L);
            when(repo.countBills(2025)).thenReturn(50L);
            when(repo.countBills(2024)).thenReturn(40L);
            when(repo.countClients()).thenReturn(1000L);
            when(repo.averageAge()).thenReturn(42.37);
            when(repo.countOfficers()).thenReturn(8L);
            when(repo.sumTransfers(2025)).thenReturn(5000.0);
            when(repo.flowsByCurrency(2025)).thenReturn(List.of(slice("TND", 100, null)));

            AnalyticsSummaryDto result = analytics.summary(2025);

            assertThat(result.getTotalClients()).isEqualTo(1000);
            assertThat(result.getAgeMoyen()).isEqualTo(42.37);
            assertThat(result.getNbGestionnaires()).isEqualTo(8);
            assertThat(result.getNbVirements()).isEqualTo(120);
            assertThat(result.getDeltaVirementsPct()).isEqualTo(20.0);
            assertThat(result.getNbFactures()).isEqualTo(50);
            assertThat(result.getDeltaFacturesPct()).isEqualTo(25.0);
            assertThat(result.getMontantVirements()).isEqualTo(5000.0);
            assertThat(result.getDevisePrincipale()).isEqualTo("TND");
        }

        @Test
        void leavesDeltaNullWhenPreviousYearHasNoData() {
            when(repo.countTransfers(2025)).thenReturn(120L);
            when(repo.countTransfers(2024)).thenReturn(0L);
            when(repo.countBills(2025)).thenReturn(50L);
            when(repo.countBills(2024)).thenReturn(0L);
            when(repo.flowsByCurrency(2025)).thenReturn(List.of());

            AnalyticsSummaryDto result = analytics.summary(2025);

            assertThat(result.getDeltaVirementsPct()).isNull();
            assertThat(result.getDeltaFacturesPct()).isNull();
            assertThat(result.getDevisePrincipale()).isNull();
        }

        @Test
        void skipsPreviousYearLookupWhenNoYearFilterIsApplied() {
            when(repo.countTransfers(0)).thenReturn(500L);
            when(repo.countBills(0)).thenReturn(200L);
            when(repo.flowsByCurrency(0)).thenReturn(List.of());

            AnalyticsSummaryDto result = analytics.summary(0);

            assertThat(result.getDeltaVirementsPct()).isNull();
            assertThat(result.getDeltaFacturesPct()).isNull();
            verify(repo, never()).countTransfers(-1);
            verify(repo, never()).countBills(-1);
        }
    }

    // ── Page Flux ────────────────────────────────────────────────────────────

    @Nested
    class Flows {

        @Test
        void summaryComputesAverageTicketAndDominantCurrency() {
            when(repo.countTransfers(2025)).thenReturn(200L);
            when(repo.countTransfers(2024)).thenReturn(160L);
            when(repo.sumTransfers(2025)).thenReturn(5000.0);
            when(repo.sumTransfers(2024)).thenReturn(4000.0);
            when(repo.flowsByCurrency(2025)).thenReturn(List.of(
                    slice("TND", 150, null), slice("EUR", 50, null)));
            when(repo.flowsByType(2025)).thenReturn(List.of(
                    slice("AC", 120, null), slice("TFR", 80, null)));

            FlowsSummaryDto result = analytics.flowsSummary(2025);

            assertThat(result.getNbOperations()).isEqualTo(200);
            assertThat(result.getDeltaOpsPct()).isEqualTo(25.0);
            assertThat(result.getMontantTotal()).isEqualTo(5000.0);
            assertThat(result.getDeltaMontantPct()).isEqualTo(25.0);
            assertThat(result.getTicketMoyen()).isEqualTo(25.0);
            assertThat(result.getDeviseDominante()).isEqualTo("TND");
            assertThat(result.getNbDevises()).isEqualTo(2);
            assertThat(result.getNbTypes()).isEqualTo(2);
        }

        @Test
        void summaryKeepsAmountsNullWhileTheAmountColumnIsNotExtracted() {
            when(repo.countTransfers(0)).thenReturn(200L);
            when(repo.sumTransfers(0)).thenReturn(null);
            when(repo.flowsByCurrency(0)).thenReturn(List.of());
            when(repo.flowsByType(0)).thenReturn(List.of());

            FlowsSummaryDto result = analytics.flowsSummary(0);

            assertThat(result.getMontantTotal()).isNull();
            assertThat(result.getTicketMoyen()).isNull();
            assertThat(result.getDeltaMontantPct()).isNull();
            assertThat(result.getDeviseDominante()).isNull();
            assertThat(result.getNbDevises()).isNull();
            assertThat(result.getNbTypes()).isNull();
        }

        @Test
        void sharesAreComputedOnAmountsWhenTheyAreSignificant() {
            when(repo.flowsByCurrency(2025)).thenReturn(List.of(
                    slice("TND", 80, 800.0), slice("EUR", 20, 1200.0)));

            List<FlowSliceDto> result = analytics.flowsByCurrency(2025);

            // Tri décroissant sur la part : l'EUR pèse plus en montant malgré moins d'opérations
            assertThat(result).extracting(FlowSliceDto::getLabel).containsExactly("EUR", "TND");
            assertThat(result.get(0).getPct()).isEqualTo(60.0);
            assertThat(result.get(0).getTicketMoyen()).isEqualTo(60.0);
            assertThat(result.get(1).getPct()).isEqualTo(40.0);
            assertThat(result.get(1).getTicketMoyen()).isEqualTo(10.0);
        }

        @Test
        void sharesFallBackToVolumeWhenAmountsAreAbsent() {
            when(repo.flowsByType(0)).thenReturn(List.of(
                    slice("AC", 75, null), slice("TFR", 25, null)));

            List<FlowSliceDto> result = analytics.flowsByType(0);

            assertThat(result).extracting(FlowSliceDto::getLabel).containsExactly("AC", "TFR");
            assertThat(result.get(0).getPct()).isEqualTo(75.0);
            assertThat(result.get(0).getMontantTotal()).isNull();
            assertThat(result.get(0).getTicketMoyen()).isNull();
            assertThat(result.get(1).getPct()).isEqualTo(25.0);
        }

        @Test
        void sharesFallBackToVolumeWhenEveryAmountIsZero() {
            when(repo.flowsByType(0)).thenReturn(List.of(
                    slice("AC", 60, 0.0), slice("TFR", 40, 0.0)));

            List<FlowSliceDto> result = analytics.flowsByType(0);

            assertThat(result.get(0).getPct()).isEqualTo(60.0);
            assertThat(result.get(1).getPct()).isEqualTo(40.0);
        }

        @Test
        void monthlyTransfersAlwaysReturnTwelvePaddedMonths() {
            when(repo.monthlyTransfers(2025)).thenReturn(List.of(month(1, 10), month(3, 5)));
            when(repo.monthlyTransfers(2024)).thenReturn(List.of(month(1, 8)));
            when(repo.monthlyAmounts(2025)).thenReturn(List.of(amount(1, 100.0)));
            when(repo.monthlyAmounts(2024)).thenReturn(List.of());

            List<MonthlyFlowDto> result = analytics.monthlyTransfers(2025);

            assertThat(result).hasSize(12);
            assertThat(result).extracting(MonthlyFlowDto::getMois)
                    .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
            assertThat(result.get(0).getNbOperations()).isEqualTo(10);
            assertThat(result.get(0).getNbOperationsN1()).isEqualTo(8);
            assertThat(result.get(0).getMontantTotal()).isEqualTo(100.0);
            assertThat(result.get(0).getMontantTotalN1()).isNull();
            assertThat(result.get(1).getNbOperations()).isZero();
            assertThat(result.get(1).getMontantTotal()).isEqualTo(0.0);
            assertThat(result.get(2).getNbOperations()).isEqualTo(5);
        }

        @Test
        void monthlyTransfersOmitPreviousYearWhenNoYearFilterIsApplied() {
            when(repo.monthlyTransfers(0)).thenReturn(List.of(month(1, 10)));
            when(repo.monthlyAmounts(0)).thenReturn(List.of());

            List<MonthlyFlowDto> result = analytics.monthlyTransfers(0);

            assertThat(result).hasSize(12);
            assertThat(result).allSatisfy(m -> {
                assertThat(m.getNbOperationsN1()).isNull();
                assertThat(m.getMontantTotal()).isNull();
                assertThat(m.getMontantTotalN1()).isNull();
            });
        }

        @Test
        void heatmapMapsEveryCell() {
            when(repo.activityHeatmap(2025)).thenReturn(List.of(
                    heatmapCell(3, 0, 42), heatmapCell(3, 6, 7)));

            List<HeatmapCellDto> result = analytics.activityHeatmap(2025);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getMois()).isEqualTo(3);
            assertThat(result.get(0).getJourSemaine()).isZero();
            assertThat(result.get(0).getNb()).isEqualTo(42);
            assertThat(result.get(1).getJourSemaine()).isEqualTo(6);
        }

        @Test
        void insightsReportDominantCurrencyDominantTypeAndTicket() {
            when(repo.flowsByCurrency(2025)).thenReturn(List.of(
                    slice("TND", 150, null), slice("EUR", 50, null)));
            when(repo.flowsByType(2025)).thenReturn(List.of(
                    slice("AC", 120, null), slice("TFR", 80, null)));
            when(repo.countTransfers(2025)).thenReturn(200L);
            when(repo.countTransfers(2024)).thenReturn(0L);
            when(repo.sumTransfers(2025)).thenReturn(5000.0);
            when(repo.sumTransfers(2024)).thenReturn(null);

            List<InsightDto> result = analytics.flowsInsights(2025);

            assertThat(result).extracting(InsightDto::getType)
                    .containsExactly("LEADER", "CONCENTRATION", "PIC_ACTIVITE");
            assertThat(result.get(0).getValue()).isEqualTo("TND");
            assertThat(result.get(0).getParams()).containsEntry("pct", 75.0).containsEntry("count", 150L);
            assertThat(result.get(1).getValue()).isEqualTo("AC");
            assertThat(result.get(2).getDetail()).isEqualTo("TND");
        }

        @Test
        void insightsIgnorePlaceholderLabelsOfUnextractedColumns() {
            when(repo.flowsByCurrency(0)).thenReturn(List.of(slice("—", 200, null)));
            when(repo.flowsByType(0)).thenReturn(List.of(slice("—", 200, null)));
            when(repo.countTransfers(0)).thenReturn(200L);
            when(repo.sumTransfers(0)).thenReturn(null);

            assertThat(analytics.flowsInsights(0)).isEmpty();
        }

        @Test
        void columnsStatusIsDelegatedToTheRepository() {
            Map<String, Boolean> status = Map.of("AMOUNT_DEBITED", false, "DEBIT_CURRENCY", true);
            when(repo.ftEnrichedColumnsStatus()).thenReturn(status);

            assertThat(analytics.flowsColumnsStatus()).isEqualTo(status);
        }
    }

    // ── Page Clients ─────────────────────────────────────────────────────────

    @Nested
    class Customers {

        @Test
        void summaryComputesFemaleShareAndDimensionCounts() {
            when(repo.countClients()).thenReturn(1000L);
            when(repo.averageAge()).thenReturn(42.36);
            when(repo.countOfficers()).thenReturn(8L);
            when(repo.migrationShare()).thenReturn(61.25);
            when(repo.countNewCustomers(2025)).thenReturn(40L);
            when(repo.countNewCustomers(2024)).thenReturn(32L);
            when(repo.customerBreakdown("gender", 10)).thenReturn(List.of(
                    breakdown("FEMALE", 40), breakdown("MALE", 60)));
            when(repo.customerBreakdown("nationality", 500)).thenReturn(List.of(
                    breakdown("TN", 900), breakdown("FR", 60), breakdown("DZ", 40)));
            when(repo.customerBreakdown("sector", 500)).thenReturn(List.of(
                    breakdown("1001", 700), breakdown("2001", 300)));

            CustomersSummaryDto result = analytics.customersSummary(2025);

            assertThat(result.getTotalClients()).isEqualTo(1000);
            assertThat(result.getAgeMoyen()).isEqualTo(42.4);
            assertThat(result.getNbGestionnaires()).isEqualTo(8);
            assertThat(result.getPctMigration()).isEqualTo(61.3);
            assertThat(result.getNouveauxClients()).isEqualTo(40L);
            assertThat(result.getDeltaNouveauxPct()).isEqualTo(25.0);
            assertThat(result.getPctFemmes()).isEqualTo(40.0);
            assertThat(result.getNbNationalites()).isEqualTo(3);
            assertThat(result.getNbSecteurs()).isEqualTo(2);
        }

        @Test
        void summaryDegradesGracefullyWhenEnrichedColumnsAreMissing() {
            when(repo.countClients()).thenReturn(1000L);
            when(repo.averageAge()).thenReturn(null);
            when(repo.migrationShare()).thenReturn(null);
            when(repo.countNewCustomers(2025)).thenReturn(null);
            when(repo.customerBreakdown("gender", 10)).thenReturn(List.of());
            when(repo.customerBreakdown("nationality", 500)).thenReturn(List.of());
            when(repo.customerBreakdown("sector", 500)).thenReturn(List.of());

            CustomersSummaryDto result = analytics.customersSummary(2025);

            assertThat(result.getAgeMoyen()).isNull();
            assertThat(result.getPctMigration()).isNull();
            assertThat(result.getNouveauxClients()).isNull();
            assertThat(result.getDeltaNouveauxPct()).isNull();
            assertThat(result.getPctFemmes()).isNull();
            assertThat(result.getNbNationalites()).isNull();
            assertThat(result.getNbSecteurs()).isNull();
            verify(repo, never()).countNewCustomers(2024);
        }

        @Test
        void ageHistogramLabelsFiveYearBandsAndCapsTheLastOne() {
            when(repo.ageHistogram()).thenReturn(List.of(
                    histogramBand(40, 30), histogramBand(85, 10)));

            List<BreakdownSliceDto> result = analytics.ageHistogram();

            assertThat(result).extracting(BreakdownSliceDto::getLabel).containsExactly("40–44", "85 +");
            assertThat(result.get(0).getValue()).isEqualTo(30);
            assertThat(result.get(0).getPct()).isEqualTo(75.0);
            assertThat(result.get(1).getPct()).isEqualTo(25.0);
        }

        @Test
        void generationsComputeShares() {
            when(repo.generations()).thenReturn(List.of(
                    breakdown("Génération X", 300), breakdown("Millennials", 100)));

            List<BreakdownSliceDto> result = analytics.generations();

            assertThat(result.get(0).getPct()).isEqualTo(75.0);
            assertThat(result.get(1).getPct()).isEqualTo(25.0);
        }

        @Test
        void agePyramidComputesShares() {
            when(repo.agePyramid()).thenReturn(List.of(
                    pyramidBand("25-34", 200), pyramidBand("35-44", 600)));

            List<BreakdownSliceDto> result = analytics.agePyramid();

            assertThat(result).extracting(BreakdownSliceDto::getLabel).containsExactly("25-34", "35-44");
            assertThat(result.get(0).getPct()).isEqualTo(25.0);
            assertThat(result.get(1).getPct()).isEqualTo(75.0);
        }

        @Test
        void breakdownComputesSharesOverTheTopTwelveValues() {
            when(repo.customerBreakdown("sector", 12)).thenReturn(List.of(
                    breakdown("1001", 700), breakdown("2001", 300)));

            List<BreakdownSliceDto> result = analytics.customersBreakdown("sector");

            assertThat(result.get(0).getPct()).isEqualTo(70.0);
            assertThat(result.get(1).getPct()).isEqualTo(30.0);
        }

        @Test
        void newMonthlyIsEmptyWhileTheAccountOpeningDateIsNotExtracted() {
            when(repo.newCustomersMonthly(2025)).thenReturn(List.of());

            assertThat(analytics.newCustomersMonthly(2025)).isEmpty();
            verify(repo, never()).newCustomersMonthly(2024);
        }

        @Test
        void newMonthlyPadsTwelveMonthsWithoutAmounts() {
            when(repo.newCustomersMonthly(2025)).thenReturn(List.of(month(2, 12)));
            when(repo.newCustomersMonthly(2024)).thenReturn(List.of(month(2, 10)));

            List<MonthlyFlowDto> result = analytics.newCustomersMonthly(2025);

            assertThat(result).hasSize(12);
            assertThat(result.get(1).getNbOperations()).isEqualTo(12);
            assertThat(result.get(1).getNbOperationsN1()).isEqualTo(10);
            assertThat(result).allSatisfy(m -> {
                assertThat(m.getMontantTotal()).isNull();
                assertThat(m.getMontantTotalN1()).isNull();
            });
        }

        @Test
        void availableDimensionsKeepOnlyExtractedColumnsSortedAlphabetically() {
            when(repo.hasColumn(AnalyticsRepository.CUS_TABLE, "SECTOR")).thenReturn(true);
            when(repo.hasColumn(AnalyticsRepository.CUS_TABLE, "GENDER")).thenReturn(true);
            when(repo.hasColumn(AnalyticsRepository.CUS_TABLE, "NATIONALITY")).thenReturn(false);
            when(repo.hasColumn(AnalyticsRepository.CUS_TABLE, "TARGET")).thenReturn(false);
            when(repo.hasColumn(AnalyticsRepository.CUS_TABLE, "CUSTOMER_STATUS")).thenReturn(false);

            assertThat(analytics.availableCustomerDimensions()).containsExactly("gender", "sector");
        }

        @Test
        void insightsUseTheAcquisitionPeakWhenTheOpeningDateIsExtracted() {
            when(repo.generations()).thenReturn(List.of(
                    breakdown("Génération X", 300), breakdown("Millennials", 100)));
            when(repo.ageShare(25, 54)).thenReturn(63.42);
            when(repo.newCustomersMonthly(2025)).thenReturn(List.of(month(2, 5), month(9, 40)));
            when(repo.newCustomersMonthly(2024)).thenReturn(List.of());

            List<InsightDto> result = analytics.customersInsights(2025);

            assertThat(result).extracting(InsightDto::getType)
                    .containsExactly("LEADER", "CONCENTRATION", "PIC_ACTIVITE");
            assertThat(result.get(0).getValue()).isEqualTo("Génération X");
            assertThat(result.get(1).getValue()).isEqualTo("63.4 %");
            assertThat(result.get(2).getValue()).isEqualTo("09/2025");
            assertThat(result.get(2).getParams()).containsEntry("count", 40L);
        }

        @Test
        void insightsFallBackToTheMigratedShareWhenAcquisitionIsUnavailable() {
            when(repo.generations()).thenReturn(List.of(breakdown("Génération X", 300)));
            when(repo.ageShare(25, 54)).thenReturn(null);
            when(repo.newCustomersMonthly(0)).thenReturn(List.of());
            when(repo.migrationShare()).thenReturn(61.25);

            List<InsightDto> result = analytics.customersInsights(0);

            assertThat(result).extracting(InsightDto::getType).containsExactly("LEADER", "PIC_ACTIVITE");
            assertThat(result.get(1).getTitleKey()).isEqualTo("analytics.customers.insight.migration");
            assertThat(result.get(1).getValue()).isEqualTo("61.3 %");
        }

        @Test
        void acquisitionYearsAreDelegatedToTheRepository() {
            when(repo.acquisitionYears()).thenReturn(List.of(2025, 2024));

            assertThat(analytics.acquisitionYears()).containsExactly(2025, 2024);
        }
    }

    // ── Page Créances ────────────────────────────────────────────────────────

    @Nested
    class Billing {

        @Test
        void summaryComputesRecoveryRateAndPaidShare() {
            when(repo.countBills(2025)).thenReturn(100L);
            when(repo.countBills(2024)).thenReturn(80L);
            when(repo.billedAmount(2025)).thenReturn(1000.0);
            when(repo.outstandingAmount(2025)).thenReturn(250.0);
            when(repo.billingBreakdown("settle", 2025, 50)).thenReturn(List.of(
                    slice("REPAID", 50, null), slice("PART.SETTLED", 20, null), slice("UNPAID", 30, null)));
            when(repo.avgSettlementDelay(2025)).thenReturn(3.456);

            BillingSummaryDto result = analytics.billingSummary(2025);

            assertThat(result.getNbFactures()).isEqualTo(100);
            assertThat(result.getDeltaFacturesPct()).isEqualTo(25.0);
            assertThat(result.getMontantFacture()).isEqualTo(1000.0);
            assertThat(result.getEncoursTotal()).isEqualTo(250.0);
            assertThat(result.getTauxRecouvrement()).isEqualTo(75.0);
            assertThat(result.getPctPayees()).isEqualTo(70.0);
            assertThat(result.getDelaiMoyenReglement()).isEqualTo(3.5);
        }

        @Test
        void summaryDegradesGracefullyWhenAmountColumnsAreMissing() {
            when(repo.countBills(0)).thenReturn(100L);
            when(repo.billedAmount(0)).thenReturn(null);
            when(repo.outstandingAmount(0)).thenReturn(null);
            when(repo.billingBreakdown("settle", 0, 50)).thenReturn(List.of());
            when(repo.avgSettlementDelay(0)).thenReturn(null);

            BillingSummaryDto result = analytics.billingSummary(0);

            assertThat(result.getMontantFacture()).isNull();
            assertThat(result.getEncoursTotal()).isNull();
            assertThat(result.getTauxRecouvrement()).isNull();
            assertThat(result.getPctPayees()).isNull();
            assertThat(result.getDelaiMoyenReglement()).isNull();
        }

        /** Non-régression : « UNPAID » et « UNSETTLED » contiennent PAID / SETTLED. */
        @Test
        void paidShareExcludesNegatedSettlementStatuses() {
            when(repo.countBills(0)).thenReturn(100L);
            when(repo.billedAmount(0)).thenReturn(null);
            when(repo.outstandingAmount(0)).thenReturn(null);
            when(repo.avgSettlementDelay(0)).thenReturn(null);
            when(repo.billingBreakdown("settle", 0, 50)).thenReturn(List.of(
                    slice("PAID", 10, null),
                    slice("SETTLED", 10, null),
                    slice("UNPAID", 40, null),
                    slice("UNSETTLED", 40, null)));

            assertThat(analytics.billingSummary(0).getPctPayees()).isEqualTo(20.0);
        }

        @Test
        void monthlyPadsTwelveMonthsAndComparesToPreviousYear() {
            when(repo.monthlyBills(2025)).thenReturn(List.of(month(5, 30)));
            when(repo.monthlyBills(2024)).thenReturn(List.of(month(5, 20)));
            when(repo.monthlyBillAmounts(2025)).thenReturn(List.of(amount(5, 900.0)));
            when(repo.monthlyBillAmounts(2024)).thenReturn(List.of(amount(5, 600.0)));

            List<MonthlyFlowDto> result = analytics.billingMonthly(2025);

            assertThat(result).hasSize(12);
            assertThat(result.get(4).getNbOperations()).isEqualTo(30);
            assertThat(result.get(4).getNbOperationsN1()).isEqualTo(20);
            assertThat(result.get(4).getMontantTotal()).isEqualTo(900.0);
            assertThat(result.get(4).getMontantTotalN1()).isEqualTo(600.0);
            assertThat(result.get(0).getMontantTotal()).isEqualTo(0.0);
        }

        @Test
        void breakdownRequestsTheTopTwelveValuesOfTheDimension() {
            when(repo.billingBreakdown("aging", 2025, 12)).thenReturn(List.of(
                    slice("CURRENT", 80, null), slice("OVERDUE", 20, null)));

            List<FlowSliceDto> result = analytics.billingBreakdown("aging", 2025);

            assertThat(result).extracting(FlowSliceDto::getLabel).containsExactly("CURRENT", "OVERDUE");
            assertThat(result.get(0).getPct()).isEqualTo(80.0);
        }

        @Test
        void availableDimensionsKeepOnlyExtractedColumnsSortedAlphabetically() {
            when(repo.hasColumn(AnalyticsRepository.BILL_TABLE, "AGING_STATUS")).thenReturn(true);
            when(repo.hasColumn(AnalyticsRepository.BILL_TABLE, "SETTLE_STATUS")).thenReturn(false);
            when(repo.hasColumn(AnalyticsRepository.BILL_TABLE, "BILL_STATUS")).thenReturn(true);
            when(repo.hasColumn(AnalyticsRepository.BILL_TABLE, "BILL_TYPE")).thenReturn(false);
            when(repo.hasColumn(AnalyticsRepository.BILL_TABLE, "PAYMENT_METHOD")).thenReturn(false);

            assertThat(analytics.availableBillingDimensions()).containsExactly("aging", "status");
        }

        @Test
        void insightsFlagALateSettlementDelay() {
            when(repo.monthlyBills(2025)).thenReturn(List.of(month(2, 5), month(11, 90)));
            when(repo.billingBreakdown("settle", 2025, 50)).thenReturn(List.of(
                    slice("REPAID", 70, null), slice("UNPAID", 30, null)));
            when(repo.avgSettlementDelay(2025)).thenReturn(3.46);

            List<InsightDto> result = analytics.billingInsights(2025);

            assertThat(result).extracting(InsightDto::getType)
                    .containsExactly("LEADER", "CONCENTRATION", "PIC_ACTIVITE");
            assertThat(result.get(0).getValue()).isEqualTo("11/2025");
            assertThat(result.get(1).getValue()).isEqualTo("70.0 %");
            assertThat(result.get(2).getValue()).isEqualTo("3.5 j");
            assertThat(result.get(2).getDetailKey()).isEqualTo("analytics.billing.insight.delayAfter");
        }

        @Test
        void insightsFlagAnEarlySettlementDelay() {
            when(repo.monthlyBills(0)).thenReturn(List.of());
            when(repo.billingBreakdown("settle", 0, 50)).thenReturn(List.of());
            when(repo.avgSettlementDelay(0)).thenReturn(-2.0);

            List<InsightDto> result = analytics.billingInsights(0);

            assertThat(result).singleElement().satisfies(insight -> {
                assertThat(insight.getValue()).isEqualTo("2.0 j");
                assertThat(insight.getDetailKey()).isEqualTo("analytics.billing.insight.delayBefore");
            });
        }

        @Test
        void agingComputesShares() {
            when(repo.billingAging()).thenReturn(List.of(
                    breakdown("CURRENT", 80), breakdown("OVERDUE", 20)));

            List<BreakdownSliceDto> result = analytics.billingAging();

            assertThat(result.get(0).getPct()).isEqualTo(80.0);
            assertThat(result.get(1).getPct()).isEqualTo(20.0);
        }
    }

    // ── Enseignements de synthèse ────────────────────────────────────────────

    @Nested
    class Insights {

        @Test
        void reportLeaderConcentrationAndActivityPeak() {
            when(repo.portfolioByOfficer(5)).thenReturn(List.of(
                    officer("BOUJBEL", 120, 41.234, 12.3456),
                    officer("KHELIFI", 100, 39.5, 10.04)));
            when(repo.countOfficers()).thenReturn(8L);
            when(repo.monthlyTransfers(2025)).thenReturn(List.of(month(2, 5), month(7, 88)));

            List<InsightDto> result = analytics.insights(2025);

            assertThat(result).extracting(InsightDto::getType)
                    .containsExactly("LEADER", "CONCENTRATION", "PIC_ACTIVITE");
            assertThat(result.get(0).getValue()).isEqualTo("BOUJBEL");
            assertThat(result.get(0).getParams()).containsEntry("count", 120L).containsEntry("pct", 12.3);
            assertThat(result.get(1).getValue()).isEqualTo("22.3 %");
            assertThat(result.get(1).getParams()).containsEntry("count", 8L);
            assertThat(result.get(2).getValue()).isEqualTo("07/2025");
        }

        @Test
        void areEmptyWhenNoDataIsAvailable() {
            when(repo.portfolioByOfficer(5)).thenReturn(List.of());
            when(repo.monthlyTransfers(0)).thenReturn(List.of());

            assertThat(analytics.insights(0)).isEmpty();
        }

        @Test
        void topOfficersRoundThePortfolioShare() {
            when(repo.portfolioByOfficer(3)).thenReturn(List.of(
                    officer("BOUJBEL", 120, 41.234, 12.3456)));

            List<OfficerPerfDto> result = analytics.topOfficers(3);

            assertThat(result).singleElement().satisfies(o -> {
                assertThat(o.getOfficer()).isEqualTo("BOUJBEL");
                assertThat(o.getNbClients()).isEqualTo(120);
                assertThat(o.getAgeMoyen()).isEqualTo(41.234);
                assertThat(o.getPartPct()).isEqualTo(12.3);
            });
        }

        @Test
        void availableYearsAreDelegatedToTheRepository() {
            when(repo.transferYears()).thenReturn(List.of(2025, 2024, 2023));

            assertThat(analytics.availableYears()).containsExactly(2025, 2024, 2023);
        }

        @Test
        void billYearsAreDelegatedToTheRepository() {
            when(repo.billYears()).thenReturn(List.of(2025, 2024));

            assertThat(analytics.billYears()).containsExactly(2025, 2024);
        }
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static Map<String, Object> slice(String label, long nb, Double montant) {
        return row("label", label, "nb", nb, "montant", montant);
    }

    private static Map<String, Object> breakdown(String label, long nb) {
        return row("label", label, "nb", nb);
    }

    private static Map<String, Object> pyramidBand(String tranche, long nb) {
        return row("tranche", tranche, "nb", nb);
    }

    private static Map<String, Object> histogramBand(int band, long nb) {
        return row("band", band, "nb", nb);
    }

    private static Map<String, Object> month(int mois, long nb) {
        return row("mois", mois, "nb", nb);
    }

    private static Map<String, Object> amount(int mois, Double montant) {
        return row("mois", mois, "montant", montant);
    }

    private static Map<String, Object> heatmapCell(int mois, int jour, long nb) {
        return row("mois", mois, "jour", jour, "nb", nb);
    }

    private static Map<String, Object> officer(String name, long nb, Double ageMoyen, double partPct) {
        return row("officer", name, "nb", nb, "age_moyen", ageMoyen, "part_pct", partPct);
    }

    /** Ligne JDBC brute : les valeurs null sont conservées, contrairement à Map.of. */
    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        return row;
    }
}