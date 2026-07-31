package wevioo.se.monitoringms.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wevioo.se.monitoringms.config.CacheConfig;
import wevioo.se.monitoringms.dto.KpiDto;
import wevioo.se.monitoringms.dto.ReconciliationDto;
import wevioo.se.monitoringms.dto.SlaStatusDto;
import wevioo.se.monitoringms.service.KpiService;
import wevioo.se.monitoringms.service.ReconciliationService;
import wevioo.se.monitoringms.service.SlaService;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Non-régression du contrat HTTP des indicateurs, dont le bornage
 * de la fenêtre d'analyse entre 1 et 90 jours.
 */
@WebMvcTest(KpiController.class)
@Import(CacheConfig.class)
class KpiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KpiService kpiService;

    @MockitoBean
    private ReconciliationService reconciliationService;

    @MockitoBean
    private SlaService slaService;

    @Test
    void getKpisDefaultsToASevenDayWindow() throws Exception {
        when(kpiService.computeKpis(7)).thenReturn(sampleKpis(7));

        mockMvc.perform(get("/api/v1/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowDays").value(7))
                .andExpect(jsonPath("$.overallSuccessRate").value(96.25))
                .andExpect(jsonPath("$.totalRowsExtracted").value(120000))
                .andExpect(jsonPath("$.rowsByTable.CUSTOMER").value(50000));

        verify(kpiService).computeKpis(7);
    }

    @Test
    void getKpisClampsAWindowAboveNinetyDays() throws Exception {
        when(kpiService.computeKpis(anyInt())).thenReturn(sampleKpis(90));

        mockMvc.perform(get("/api/v1/kpis").param("days", "365"))
                .andExpect(status().isOk());

        verify(kpiService).computeKpis(90);
    }

    @Test
    void getKpisClampsAWindowBelowOneDay() throws Exception {
        when(kpiService.computeKpis(anyInt())).thenReturn(sampleKpis(1));

        mockMvc.perform(get("/api/v1/kpis").param("days", "0"))
                .andExpect(status().isOk());

        verify(kpiService).computeKpis(1);
    }

    @Test
    void getReconciliationExposesTheOracleToSqlServerComparison() throws Exception {
        when(reconciliationService.getLatestReconciliation()).thenReturn(List.of(
                ReconciliationDto.builder()
                        .tableName("CUSTOMER")
                        .businessDate(20260728)
                        .oracleCount(1000)
                        .sqlServerCount(970)
                        .delta(30)
                        .reconciliationRate(97.0)
                        .reconciled(true)
                        .status("Conforme")
                        .build()));

        mockMvc.perform(get("/api/v1/kpis/reconciliation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tableName").value("CUSTOMER"))
                .andExpect(jsonPath("$[0].delta").value(30))
                .andExpect(jsonPath("$[0].reconciliationRate").value(97.0))
                .andExpect(jsonPath("$[0].reconciled").value(true))
                .andExpect(jsonPath("$[0].status").value("Conforme"));
    }

    @Test
    void getReconciliationByTableForwardsTheTableName() throws Exception {
        when(reconciliationService.getReconciliationForTable("FT_HIS")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/kpis/reconciliation/FT_HIS"))
                .andExpect(status().isOk());

        verify(reconciliationService).getReconciliationForTable("FT_HIS");
    }

    @Test
    void getAllSlaReturnsEveryMonitoredJob() throws Exception {
        when(slaService.getAllSlaStatuses()).thenReturn(List.of(
                SlaStatusDto.builder()
                        .jobName("T24")
                        .slaDeadline(LocalTime.of(8, 0))
                        .slaBreached(false)
                        .lateBySeconds(0)
                        .status("Dans les délais")
                        .build()));

        mockMvc.perform(get("/api/v1/kpis/sla"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobName").value("T24"))
                .andExpect(jsonPath("$[0].slaBreached").value(false))
                .andExpect(jsonPath("$[0].status").value("Dans les délais"));
    }

    @Test
    void getSlaByJobForwardsTheJobName() throws Exception {
        when(slaService.getSlaStatus("CUSTOMER.JOB")).thenReturn(SlaStatusDto.builder()
                .jobName("CUSTOMER.JOB")
                .slaDeadline(LocalTime.of(7, 0))
                .slaBreached(true)
                .lateBySeconds(900)
                .status("Délai dépassé")
                .build());

        mockMvc.perform(get("/api/v1/kpis/sla/CUSTOMER.JOB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobName").value("CUSTOMER.JOB"))
                .andExpect(jsonPath("$.slaBreached").value(true))
                .andExpect(jsonPath("$.lateBySeconds").value(900));
    }

    private static KpiDto sampleKpis(int windowDays) {
        return KpiDto.builder()
                .windowDays(windowDays)
                .overallSuccessRate(96.25)
                .successRateByJob(Map.of("T24", 100.0))
                .totalRowsExtracted(120000L)
                .rowsByTable(Map.of("CUSTOMER", 50000L))
                .avgThroughputPerSec(1250.5)
                .throughputByJob(Map.of("T24", 900.0))
                .avgDurationSeconds(320.4)
                .avgDurationByJob(Map.of("T24", 320.4))
                .failureCount(1)
                .consecutiveFailureCount(0)
                .volumeTrend(List.of())
                .build();
    }
}
