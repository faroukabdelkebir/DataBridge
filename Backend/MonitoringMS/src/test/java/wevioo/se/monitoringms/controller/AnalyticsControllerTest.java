package wevioo.se.monitoringms.controller;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wevioo.se.monitoringms.config.CacheConfig;
import wevioo.se.monitoringms.dto.analytics.*;
import wevioo.se.monitoringms.service.AnalyticsService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Non-régression du contrat HTTP analytique : routes, valeurs par défaut
 * des filtres (année = 0 → tout l'historique) et sérialisation JSON.
 */
@WebMvcTest(AnalyticsController.class)
@Import(CacheConfig.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analytics;

    @Nested
    class SynthesePage {

        @Test
        void summaryDefaultsToTheWholeHistory() throws Exception {
            when(analytics.summary(0)).thenReturn(AnalyticsSummaryDto.builder()
                    .totalClients(1000)
                    .ageMoyen(42.4)
                    .nbGestionnaires(8)
                    .nbVirements(120)
                    .deltaVirementsPct(20.0)
                    .nbFactures(50)
                    .montantVirements(5000.0)
                    .devisePrincipale("TND")
                    .build());

            mockMvc.perform(get("/api/v1/analytics/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalClients").value(1000))
                    .andExpect(jsonPath("$.ageMoyen").value(42.4))
                    .andExpect(jsonPath("$.deltaVirementsPct").value(20.0))
                    .andExpect(jsonPath("$.deltaFacturesPct").doesNotExist())
                    .andExpect(jsonPath("$.devisePrincipale").value("TND"));

            verify(analytics).summary(0);
        }

        @Test
        void summaryHonoursTheYearFilter() throws Exception {
            when(analytics.summary(2025)).thenReturn(AnalyticsSummaryDto.builder().build());

            mockMvc.perform(get("/api/v1/analytics/summary").param("year", "2025"))
                    .andExpect(status().isOk());

            verify(analytics).summary(2025);
        }

        @Test
        void monthlyTransfersReturnTheTwelveMonthSeries() throws Exception {
            when(analytics.monthlyTransfers(2025)).thenReturn(List.of(MonthlyFlowDto.builder()
                    .mois(1).nbOperations(10).nbOperationsN1(8L).montantTotal(100.0).build()));

            mockMvc.perform(get("/api/v1/analytics/transfers/monthly").param("year", "2025"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].mois").value(1))
                    .andExpect(jsonPath("$[0].nbOperations").value(10))
                    .andExpect(jsonPath("$[0].nbOperationsN1").value(8));
        }

        @Test
        void agePyramidReturnsTheBands() throws Exception {
            when(analytics.agePyramid()).thenReturn(List.of(
                    BreakdownSliceDto.builder().label("25-34").value(200).pct(25.0).build()));

            mockMvc.perform(get("/api/v1/analytics/customers/age-pyramid"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].label").value("25-34"))
                    .andExpect(jsonPath("$[0].value").value(200))
                    .andExpect(jsonPath("$[0].pct").value(25.0));
        }

        @Test
        void byOfficerDefaultsToTenOfficers() throws Exception {
            when(analytics.topOfficers(10)).thenReturn(List.of(OfficerPerfDto.builder()
                    .officer("BOUJBEL").nbClients(120).ageMoyen(41.2).partPct(12.3).build()));

            mockMvc.perform(get("/api/v1/analytics/customers/by-officer"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].officer").value("BOUJBEL"))
                    .andExpect(jsonPath("$[0].partPct").value(12.3));

            verify(analytics).topOfficers(10);
        }

        @Test
        void byOfficerClampsTheRequestedCountToFifty() throws Exception {
            when(analytics.topOfficers(anyInt())).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/analytics/customers/by-officer").param("top", "500"))
                    .andExpect(status().isOk());

            verify(analytics).topOfficers(50);
        }

        @Test
        void byOfficerClampsANonPositiveCountToOne() throws Exception {
            when(analytics.topOfficers(anyInt())).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/analytics/customers/by-officer").param("top", "0"))
                    .andExpect(status().isOk());

            verify(analytics).topOfficers(1);
        }

        @Test
        void yearsListTheAvailableFilters() throws Exception {
            when(analytics.availableYears()).thenReturn(List.of(2025, 2024));

            mockMvc.perform(get("/api/v1/analytics/years"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value(2025))
                    .andExpect(jsonPath("$[1]").value(2024));
        }

        @Test
        void insightsExposeTheI18nKeysAndParameters() throws Exception {
            when(analytics.insights(0)).thenReturn(List.of(InsightDto.builder()
                    .type("LEADER")
                    .titleKey("analytics.insight.leader")
                    .value("BOUJBEL")
                    .detailKey("analytics.insight.leaderDetail")
                    .params(Map.of("count", 120L, "pct", 12.3))
                    .build()));

            mockMvc.perform(get("/api/v1/analytics/insights"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].type").value("LEADER"))
                    .andExpect(jsonPath("$[0].titleKey").value("analytics.insight.leader"))
                    .andExpect(jsonPath("$[0].value").value("BOUJBEL"))
                    .andExpect(jsonPath("$[0].params.count").value(120))
                    .andExpect(jsonPath("$[0].params.pct").value(12.3));
        }
    }

    @Nested
    class FluxPage {

        @Test
        void summaryDefaultsToTheWholeHistory() throws Exception {
            when(analytics.flowsSummary(0)).thenReturn(FlowsSummaryDto.builder()
                    .nbOperations(200)
                    .deltaOpsPct(25.0)
                    .montantTotal(5000.0)
                    .ticketMoyen(25.0)
                    .deviseDominante("TND")
                    .nbDevises(2)
                    .build());

            mockMvc.perform(get("/api/v1/analytics/flows/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nbOperations").value(200))
                    .andExpect(jsonPath("$.ticketMoyen").value(25.0))
                    .andExpect(jsonPath("$.nbTypes").doesNotExist());

            verify(analytics).flowsSummary(0);
        }

        @Test
        void byCurrencyReturnsTheSlices() throws Exception {
            when(analytics.flowsByCurrency(2025)).thenReturn(List.of(FlowSliceDto.builder()
                    .label("TND").nbOperations(80).montantTotal(800.0).ticketMoyen(10.0).pct(40.0).build()));

            mockMvc.perform(get("/api/v1/analytics/flows/by-currency").param("year", "2025"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].label").value("TND"))
                    .andExpect(jsonPath("$[0].ticketMoyen").value(10.0))
                    .andExpect(jsonPath("$[0].pct").value(40.0));
        }

        @Test
        void byTypeReturnsAnEmptyArrayWhileTheColumnIsNotExtracted() throws Exception {
            when(analytics.flowsByType(0)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/analytics/flows/by-type"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void heatmapReturnsTheCells() throws Exception {
            when(analytics.activityHeatmap(0)).thenReturn(List.of(
                    HeatmapCellDto.builder().mois(3).jourSemaine(0).nb(42).build()));

            mockMvc.perform(get("/api/v1/analytics/flows/heatmap"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].mois").value(3))
                    .andExpect(jsonPath("$[0].jourSemaine").value(0))
                    .andExpect(jsonPath("$[0].nb").value(42));
        }

        @Test
        void columnsStatusDrivesTheUnlockPanel() throws Exception {
            when(analytics.flowsColumnsStatus()).thenReturn(
                    Map.of("AMOUNT_DEBITED", false, "DEBIT_CURRENCY", true));

            mockMvc.perform(get("/api/v1/analytics/flows/columns-status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.AMOUNT_DEBITED").value(false))
                    .andExpect(jsonPath("$.DEBIT_CURRENCY").value(true));
        }

        @Test
        void insightsAreExposed() throws Exception {
            when(analytics.flowsInsights(0)).thenReturn(List.of(InsightDto.builder()
                    .type("LEADER").titleKey("analytics.flows.insight.currency").value("TND").build()));

            mockMvc.perform(get("/api/v1/analytics/flows/insights"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].value").value("TND"));
        }
    }

    @Nested
    class ClientsPage {

        @Test
        void summaryDefaultsToTheWholeHistory() throws Exception {
            when(analytics.customersSummary(0)).thenReturn(CustomersSummaryDto.builder()
                    .totalClients(1000)
                    .ageMoyen(42.4)
                    .nbGestionnaires(8)
                    .pctMigration(61.3)
                    .pctFemmes(40.0)
                    .nbNationalites(3)
                    .build());

            mockMvc.perform(get("/api/v1/analytics/customers/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalClients").value(1000))
                    .andExpect(jsonPath("$.pctMigration").value(61.3))
                    .andExpect(jsonPath("$.pctFemmes").value(40.0))
                    .andExpect(jsonPath("$.nouveauxClients").doesNotExist());

            verify(analytics).customersSummary(0);
        }

        @Test
        void ageHistogramReturnsTheBands() throws Exception {
            when(analytics.ageHistogram()).thenReturn(List.of(
                    BreakdownSliceDto.builder().label("40–44").value(30).pct(75.0).build()));

            mockMvc.perform(get("/api/v1/analytics/customers/age-histogram"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].label").value("40–44"));
        }

        @Test
        void generationsReturnTheSlices() throws Exception {
            when(analytics.generations()).thenReturn(List.of(
                    BreakdownSliceDto.builder().label("Génération X").value(300).pct(75.0).build()));

            mockMvc.perform(get("/api/v1/analytics/customers/generations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].label").value("Génération X"));
        }

        @Test
        void newMonthlyIsEmptyWhileTheOpeningDateIsNotExtracted() throws Exception {
            when(analytics.newCustomersMonthly(0)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/analytics/customers/new-monthly"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        void breakdownDefaultsToTheSectorDimension() throws Exception {
            when(analytics.customersBreakdown("sector")).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/analytics/customers/breakdown"))
                    .andExpect(status().isOk());

            verify(analytics).customersBreakdown("sector");
        }

        @Test
        void breakdownHonoursTheRequestedDimension() throws Exception {
            when(analytics.customersBreakdown("nationality")).thenReturn(List.of(
                    BreakdownSliceDto.builder().label("TN").value(900).pct(90.0).build()));

            mockMvc.perform(get("/api/v1/analytics/customers/breakdown").param("by", "nationality"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].label").value("TN"));

            verify(analytics).customersBreakdown("nationality");
        }

        @Test
        void dimensionsListTheExtractedColumns() throws Exception {
            when(analytics.availableCustomerDimensions()).thenReturn(List.of("gender", "sector"));

            mockMvc.perform(get("/api/v1/analytics/customers/dimensions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value("gender"))
                    .andExpect(jsonPath("$[1]").value("sector"));
        }

        @Test
        void columnsStatusDrivesTheUnlockPanel() throws Exception {
            when(analytics.customersColumnsStatus()).thenReturn(Map.of("GENDER", true));

            mockMvc.perform(get("/api/v1/analytics/customers/columns-status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.GENDER").value(true));
        }

        @Test
        void acquisitionYearsListTheAvailableFilters() throws Exception {
            when(analytics.acquisitionYears()).thenReturn(List.of(2025));

            mockMvc.perform(get("/api/v1/analytics/customers/acquisition-years"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value(2025));
        }

        @Test
        void insightsAreExposed() throws Exception {
            when(analytics.customersInsights(0)).thenReturn(List.of(InsightDto.builder()
                    .type("CONCENTRATION")
                    .titleKey("analytics.customers.insight.core")
                    .value("63.4 %")
                    .build()));

            mockMvc.perform(get("/api/v1/analytics/customers/insights"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].value").value("63.4 %"));
        }
    }

    @Nested
    class CreancesPage {

        @Test
        void summaryDefaultsToTheWholeHistory() throws Exception {
            when(analytics.billingSummary(0)).thenReturn(BillingSummaryDto.builder()
                    .nbFactures(100)
                    .deltaFacturesPct(25.0)
                    .montantFacture(1000.0)
                    .encoursTotal(250.0)
                    .tauxRecouvrement(75.0)
                    .pctPayees(70.0)
                    .build());

            mockMvc.perform(get("/api/v1/analytics/billing/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nbFactures").value(100))
                    .andExpect(jsonPath("$.tauxRecouvrement").value(75.0))
                    .andExpect(jsonPath("$.pctPayees").value(70.0))
                    .andExpect(jsonPath("$.delaiMoyenReglement").doesNotExist());

            verify(analytics).billingSummary(0);
        }

        @Test
        void monthlyReturnsTheSeries() throws Exception {
            when(analytics.billingMonthly(2025)).thenReturn(List.of(MonthlyFlowDto.builder()
                    .mois(5).nbOperations(30).nbOperationsN1(20L).montantTotal(900.0).build()));

            mockMvc.perform(get("/api/v1/analytics/billing/monthly").param("year", "2025"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].mois").value(5))
                    .andExpect(jsonPath("$[0].montantTotal").value(900.0));
        }

        @Test
        void breakdownDefaultsToTheAgingDimensionAndWholeHistory() throws Exception {
            when(analytics.billingBreakdown("aging", 0)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/analytics/billing/breakdown"))
                    .andExpect(status().isOk());

            verify(analytics).billingBreakdown("aging", 0);
        }

        @Test
        void breakdownHonoursDimensionAndYear() throws Exception {
            when(analytics.billingBreakdown("settle", 2025)).thenReturn(List.of(FlowSliceDto.builder()
                    .label("REPAID").nbOperations(70).pct(70.0).build()));

            mockMvc.perform(get("/api/v1/analytics/billing/breakdown")
                            .param("by", "settle").param("year", "2025"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].label").value("REPAID"));

            verify(analytics).billingBreakdown("settle", 2025);
        }

        @Test
        void dimensionsListTheExtractedColumns() throws Exception {
            when(analytics.availableBillingDimensions()).thenReturn(List.of("aging", "status"));

            mockMvc.perform(get("/api/v1/analytics/billing/dimensions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]").value("aging"));
        }

        @Test
        void columnsStatusDrivesTheUnlockPanel() throws Exception {
            when(analytics.billingColumnsStatus()).thenReturn(Map.of("OS_TOTAL_AMOUNT", false));

            mockMvc.perform(get("/api/v1/analytics/billing/columns-status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.OS_TOTAL_AMOUNT").value(false));
        }

        @Test
        void billYearsListTheAvailableFilters() throws Exception {
            when(analytics.billYears()).thenReturn(List.of(2025, 2024));

            mockMvc.perform(get("/api/v1/analytics/billing/years"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        void agingReturnsTheSlices() throws Exception {
            when(analytics.billingAging()).thenReturn(List.of(
                    BreakdownSliceDto.builder().label("CURRENT").value(80).pct(80.0).build()));

            mockMvc.perform(get("/api/v1/analytics/billing/aging"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].label").value("CURRENT"));
        }

        @Test
        void insightsAreExposed() throws Exception {
            when(analytics.billingInsights(0)).thenReturn(List.of(InsightDto.builder()
                    .type("PIC_ACTIVITE")
                    .titleKey("analytics.billing.insight.delay")
                    .value("3.5 j")
                    .detailKey("analytics.billing.insight.delayAfter")
                    .build()));

            mockMvc.perform(get("/api/v1/analytics/billing/insights"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].detailKey").value("analytics.billing.insight.delayAfter"));
        }
    }
}
