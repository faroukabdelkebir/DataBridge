package wevioo.se.monitoringms.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wevioo.se.monitoringms.config.CacheConfig;
import wevioo.se.monitoringms.dto.AlertDto;
import wevioo.se.monitoringms.dto.DashboardSummaryDto;
import wevioo.se.monitoringms.dto.JobExecutionDto;
import wevioo.se.monitoringms.service.DashboardService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de non-régression du contrat HTTP du tableau de bord.
 */
@WebMvcTest(DashboardController.class)
@Import(CacheConfig.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    void getSummaryExposesTheDashboardContract() throws Exception {
        when(dashboardService.getSummary()).thenReturn(DashboardSummaryDto.builder()
                .runningJobsCount(2)
                .completedTodayCount(11)
                .failedTodayCount(1)
                .successRate7d(91.5)
                .cobStatus("Arrêté")
                .lastBusinessDate("2026-07-27")
                .liveJobs(List.of(JobExecutionDto.builder()
                        .jobExecutionId(42L).jobName("T24").status("STARTED").running(true).build()))
                .recentExecutions(List.of())
                .activeAlerts(List.of(AlertDto.builder()
                        .alertKey("JOB_FAILURE_T24").severity("WARNING").build()))
                .generatedAt(LocalDateTime.of(2026, 7, 28, 8, 0))
                .build());

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runningJobsCount").value(2))
                .andExpect(jsonPath("$.completedTodayCount").value(11))
                .andExpect(jsonPath("$.failedTodayCount").value(1))
                .andExpect(jsonPath("$.successRate7d").value(91.5))
                .andExpect(jsonPath("$.cobStatus").value("Arrêté"))
                .andExpect(jsonPath("$.lastBusinessDate").value("2026-07-27"))
                .andExpect(jsonPath("$.liveJobs[0].jobExecutionId").value(42))
                .andExpect(jsonPath("$.liveJobs[0].running").value(true))
                .andExpect(jsonPath("$.activeAlerts[0].alertKey").value("JOB_FAILURE_T24"));
    }

    @Test
    void getLiveJobsReturnsOnlyRunningExecutions() throws Exception {
        when(dashboardService.getLiveJobs()).thenReturn(List.of(
                JobExecutionDto.builder().jobExecutionId(7L).jobName("CUSTOMER.JOB").running(true).build()));

        mockMvc.perform(get("/api/v1/dashboard/jobs/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].jobName").value("CUSTOMER.JOB"));
    }

    @Test
    void getLiveJobsReturnsEmptyArrayWhenNoJobIsRunning() throws Exception {
        when(dashboardService.getLiveJobs()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/dashboard/jobs/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}