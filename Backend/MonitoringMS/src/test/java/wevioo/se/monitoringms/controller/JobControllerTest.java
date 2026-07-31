package wevioo.se.monitoringms.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import wevioo.se.monitoringms.config.CacheConfig;
import wevioo.se.monitoringms.dto.JobExecutionDto;
import wevioo.se.monitoringms.dto.StepExecutionDto;
import wevioo.se.monitoringms.service.JobService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Non-régression du contrat HTTP de l'historique des traitements,
 * notamment les valeurs par défaut des paramètres de requête.
 */
@WebMvcTest(JobController.class)
@Import(CacheConfig.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobService jobService;

    @Test
    void getRecentDefaultsToFiftyResults() throws Exception {
        when(jobService.getRecent(50)).thenReturn(List.of(sampleJob()));

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobExecutionId").value(42))
                .andExpect(jsonPath("$[0].jobName").value("T24"))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].totalReadCount").value(1000))
                .andExpect(jsonPath("$[0].totalWriteCount").value(980));

        verify(jobService).getRecent(50);
    }

    @Test
    void getRecentHonoursAnExplicitLimit() throws Exception {
        when(jobService.getRecent(5)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/jobs").param("limit", "5"))
                .andExpect(status().isOk());

        verify(jobService).getRecent(5);
    }

    @Test
    void getHistoryDefaultsToSevenDays() throws Exception {
        when(jobService.getByDays(7)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/jobs/history"))
                .andExpect(status().isOk());

        verify(jobService).getByDays(7);
    }

    @Test
    void getHistoryHonoursAnExplicitWindow() throws Exception {
        when(jobService.getByDays(30)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/jobs/history").param("days", "30"))
                .andExpect(status().isOk());

        verify(jobService).getByDays(30);
    }

    @Test
    void getByIdReturnsTheExecutionDetail() throws Exception {
        when(jobService.getById(42L)).thenReturn(sampleJob());

        mockMvc.perform(get("/api/v1/jobs/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobExecutionId").value(42))
                .andExpect(jsonPath("$.durationSeconds").value(1800));
    }

    @Test
    void getByIdReturnsNotFoundForAnUnknownExecution() throws Exception {
        when(jobService.getById(999L)).thenThrow(new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Exécution de traitement introuvable : 999"));

        mockMvc.perform(get("/api/v1/jobs/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getStepsReturnsTheDrillDown() throws Exception {
        when(jobService.getSteps(42L)).thenReturn(List.of(StepExecutionDto.builder()
                .stepExecutionId(101L)
                .jobExecutionId(42L)
                .stepName("extractCustomer")
                .status("COMPLETED")
                .readCount(1000)
                .writeCount(980)
                .skipCount(6)
                .throughputPerSecond(49.0)
                .build()));

        mockMvc.perform(get("/api/v1/jobs/42/steps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stepName").value("extractCustomer"))
                .andExpect(jsonPath("$[0].skipCount").value(6))
                .andExpect(jsonPath("$[0].throughputPerSecond").value(49.0));
    }

    @Test
    void getStepsReturnsNotFoundForAnUnknownExecution() throws Exception {
        when(jobService.getSteps(999L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/v1/jobs/999/steps"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByNameDefaultsToTwentyResults() throws Exception {
        when(jobService.getByJobName("CUSTOMER.JOB", 20)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/jobs/by-name/CUSTOMER.JOB"))
                .andExpect(status().isOk());

        verify(jobService).getByJobName("CUSTOMER.JOB", 20);
    }

    @Test
    void getByNameHonoursAnExplicitLimit() throws Exception {
        when(jobService.getByJobName("T24", 3)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/jobs/by-name/T24").param("limit", "3"))
                .andExpect(status().isOk());

        verify(jobService).getByJobName("T24", 3);
    }

    private static JobExecutionDto sampleJob() {
        return JobExecutionDto.builder()
                .jobExecutionId(42L)
                .jobInstanceId(7L)
                .jobName("T24")
                .status("COMPLETED")
                .exitCode("COMPLETED")
                .startTime(LocalDateTime.of(2026, 7, 28, 5, 0))
                .endTime(LocalDateTime.of(2026, 7, 28, 5, 30))
                .durationSeconds(1800L)
                .totalReadCount(1000)
                .totalWriteCount(980)
                .totalSkipCount(6)
                .build();
    }
}
