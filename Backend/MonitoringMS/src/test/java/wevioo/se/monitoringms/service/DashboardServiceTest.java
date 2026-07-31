package wevioo.se.monitoringms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wevioo.se.monitoringms.dto.AlertDto;
import wevioo.se.monitoringms.dto.DashboardSummaryDto;
import wevioo.se.monitoringms.dto.JobExecutionDto;
import wevioo.se.monitoringms.model.BatchJobExecution;
import wevioo.se.monitoringms.repository.BatchExecutionRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private BatchExecutionRepository batchRepo;

    @Mock
    private KpiService kpiService;

    @Mock
    private AlertService alertService;

    @Mock
    private DisplayTranslationService displayTranslationService;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(batchRepo, kpiService, alertService, displayTranslationService);
    }

    @Test
    void getSummaryAggregatesCountersJobsAndAlerts() {
        BatchJobExecution running = job(1L, "T24", "STARTED", null);
        BatchJobExecution failed  = job(2L, "CUSTOMER.JOB", "FAILED", LocalDateTime.of(2026, 7, 28, 6, 30));

        when(batchRepo.findRunning()).thenReturn(List.of(running));
        when(batchRepo.findRecent(10)).thenReturn(List.of(running, failed));
        when(batchRepo.countCompletedToday()).thenReturn(12);
        when(batchRepo.countFailedToday()).thenReturn(1);
        when(batchRepo.computeSuccessRate(7)).thenReturn(92.3);
        when(batchRepo.findLastSuccessfulByJobName("T24"))
                .thenReturn(Optional.of(job(3L, "T24", "COMPLETED", LocalDateTime.of(2026, 7, 27, 7, 45))));
        when(alertService.getActiveAlerts()).thenReturn(List.of(
                AlertDto.builder().alertKey("SCHEMA_DRIFT").severity("WARNING").build()));

        DashboardSummaryDto summary = dashboardService.getSummary();

        assertThat(summary.getRunningJobsCount()).isEqualTo(1);
        assertThat(summary.getCompletedTodayCount()).isEqualTo(12);
        assertThat(summary.getFailedTodayCount()).isEqualTo(1);
        assertThat(summary.getSuccessRate7d()).isEqualTo(92.3);
        assertThat(summary.getCobStatus()).isEqualTo("Arrêté");
        assertThat(summary.getLastBusinessDate()).isEqualTo("2026-07-27");
        assertThat(summary.getLiveJobs()).extracting(JobExecutionDto::getJobExecutionId).containsExactly(1L);
        assertThat(summary.getRecentExecutions()).extracting(JobExecutionDto::getJobExecutionId).containsExactly(1L, 2L);
        assertThat(summary.getActiveAlerts()).extracting(AlertDto::getAlertKey).containsExactly("SCHEMA_DRIFT");
        assertThat(summary.getGeneratedAt()).isNotNull();
    }

    @Test
    void getSummaryReportsLastBusinessDateAsUnavailableWhenT24NeverSucceeded() {
        stubEmptySummary();
        when(batchRepo.findLastSuccessfulByJobName("T24")).thenReturn(Optional.empty());

        assertThat(dashboardService.getSummary().getLastBusinessDate()).isEqualTo("Non disponible");
    }

    @Test
    void getSummaryReportsLastBusinessDateAsUnavailableWhenEndTimeIsMissing() {
        stubEmptySummary();
        when(batchRepo.findLastSuccessfulByJobName("T24"))
                .thenReturn(Optional.of(job(9L, "T24", "COMPLETED", null)));

        assertThat(dashboardService.getSummary().getLastBusinessDate()).isEqualTo("Non disponible");
    }

    @Test
    void getLiveJobsMapsRunningAndFailedFlags() {
        BatchJobExecution running = job(1L, "T24", "STARTED", null);
        running.setStartTime(LocalDateTime.now().minusSeconds(30));
        when(batchRepo.findRunning()).thenReturn(List.of(running));

        List<JobExecutionDto> liveJobs = dashboardService.getLiveJobs();

        assertThat(liveJobs).singleElement().satisfies(dto -> {
            assertThat(dto.getJobName()).isEqualTo("T24");
            assertThat(dto.isRunning()).isTrue();
            assertThat(dto.isFailed()).isFalse();
            assertThat(dto.getDurationSeconds()).isNotNull();
        });
    }

    @Test
    void getLiveJobsReturnsEmptyListWhenNothingIsRunning() {
        when(batchRepo.findRunning()).thenReturn(List.of());

        assertThat(dashboardService.getLiveJobs()).isEmpty();
    }

    private void stubEmptySummary() {
        when(batchRepo.findRunning()).thenReturn(List.of());
        when(batchRepo.findRecent(10)).thenReturn(List.of());
        when(alertService.getActiveAlerts()).thenReturn(List.of());
    }

    private static BatchJobExecution job(long id, String name, String status, LocalDateTime endTime) {
        return BatchJobExecution.builder()
                .jobExecutionId(id)
                .jobInstanceId(id * 10)
                .jobName(name)
                .status(status)
                .exitCode("FAILED".equals(status) ? "FAILED" : "COMPLETED")
                .startTime(endTime != null ? endTime.minusMinutes(5) : null)
                .endTime(endTime)
                .createTime(LocalDateTime.of(2026, 7, 28, 5, 0))
                .build();
    }
}