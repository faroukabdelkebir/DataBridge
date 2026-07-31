package wevioo.se.monitoringms.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wevioo.se.monitoringms.config.CacheConfig;
import wevioo.se.monitoringms.dto.DailyVolumeDto;
import wevioo.se.monitoringms.dto.KpiDto;
import wevioo.se.monitoringms.service.ReportService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import(CacheConfig.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @Test
    void getDailyReportExposesKpisAndOverallStatus() throws Exception {
        when(reportService.generateDailyReport()).thenReturn(ReportService.DailyReport.builder()
                .reportDate(LocalDate.of(2026, 7, 28))
                .generatedAt(LocalDateTime.of(2026, 7, 28, 8, 0))
                .kpis1Day(sampleKpis(1))
                .kpis7Days(sampleKpis(7))
                .reconciliations(List.of())
                .slaStatuses(List.of())
                .activeAlerts(List.of(ReportService.AlertSummary.builder()
                        .type("SLA_BREACH")
                        .severity("CRITICAL")
                        .jobName("T24")
                        .message("SLA dépassé")
                        .build()))
                .overallStatus("Dégradé")
                .build());

        mockMvc.perform(get("/api/v1/reports/daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportDate").value("2026-07-28"))
                .andExpect(jsonPath("$.overallStatus").value("Dégradé"))
                .andExpect(jsonPath("$.kpis1Day.windowDays").value(1))
                .andExpect(jsonPath("$.kpis7Days.windowDays").value(7))
                .andExpect(jsonPath("$.activeAlerts[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$.activeAlerts[0].jobName").value("T24"));
    }

    @Test
    void getWeeklyReportExposesTheTrendAndTopVolumeTable() throws Exception {
        when(reportService.generateWeeklyReport()).thenReturn(ReportService.WeeklyReport.builder()
                .weekStart(LocalDate.of(2026, 7, 22))
                .weekEnd(LocalDate.of(2026, 7, 28))
                .generatedAt(LocalDateTime.of(2026, 7, 28, 8, 0))
                .kpis(sampleKpis(7))
                .successRateDeltaVsPreviousWeek(-1.25)
                .topVolumeTable("CUSTOMER")
                .trend(List.of(DailyVolumeDto.builder()
                        .date("20260728").rowsRead(1000).rowsWritten(980).jobCount(4).successRate(75.0).build()))
                .build());

        mockMvc.perform(get("/api/v1/reports/weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2026-07-22"))
                .andExpect(jsonPath("$.weekEnd").value("2026-07-28"))
                .andExpect(jsonPath("$.successRateDeltaVsPreviousWeek").value(-1.25))
                .andExpect(jsonPath("$.topVolumeTable").value("CUSTOMER"))
                .andExpect(jsonPath("$.trend[0].date").value("20260728"))
                .andExpect(jsonPath("$.trend[0].successRate").value(75.0));
    }

    private static KpiDto sampleKpis(int windowDays) {
        return KpiDto.builder()
                .windowDays(windowDays)
                .overallSuccessRate(96.25)
                .successRateByJob(Map.of())
                .rowsByTable(Map.of())
                .throughputByJob(Map.of())
                .avgDurationByJob(Map.of())
                .volumeTrend(List.of())
                .build();
    }
}
