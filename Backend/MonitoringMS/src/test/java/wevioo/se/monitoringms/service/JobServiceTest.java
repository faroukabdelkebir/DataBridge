package wevioo.se.monitoringms.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import wevioo.se.monitoringms.dto.JobExecutionDto;
import wevioo.se.monitoringms.dto.StepExecutionDto;
import wevioo.se.monitoringms.model.BatchJobExecution;
import wevioo.se.monitoringms.model.BatchStepExecution;
import wevioo.se.monitoringms.model.StatsRecord;
import wevioo.se.monitoringms.repository.BatchExecutionRepository;
import wevioo.se.monitoringms.repository.StatsRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private BatchExecutionRepository batchRepo;

    @Mock
    private StatsRepository statsRepo;

    @Mock
    private DisplayTranslationService displayTranslationService;

    @InjectMocks
    private JobService jobService;

    @Test
    void getByIdEnrichesJobWithStepAggregatesAndReconciliation() {
        BatchJobExecution job = job(42L, "COMPLETED", "x".repeat(510));
        when(batchRepo.findById(42L)).thenReturn(Optional.of(job));
        when(batchRepo.findStepsByJobExecutionId(42L)).thenReturn(List.of(
                step(1L, 42L, 100L, 80L, 1L, 2L, 3L),
                step(2L, 42L, null, 20L, null, null, 4L)
        ));
        when(statsRepo.findByTable("", 1)).thenReturn(List.of(
                StatsRecord.builder()
                        .jobExecutionId(42L)
                        .mssqlTable("CUSTOMER")
                        .businessDate(20260728)
                        .oracleCount(100)
                        .sqlCount(96)
                        .build()
        ));

        JobExecutionDto result = jobService.getById(42L);

        assertThat(result.getJobExecutionId()).isEqualTo(42L);
        assertThat(result.getTotalReadCount()).isEqualTo(100L);
        assertThat(result.getTotalWriteCount()).isEqualTo(100L);
        assertThat(result.getTotalSkipCount()).isEqualTo(10L);
        assertThat(result.getThroughputPerSecond()).isEqualTo(10.0);
        assertThat(result.getExitMessage()).hasSize(503).endsWith("...");
        assertThat(result.getReconciliation().getTableName()).isEqualTo("CUSTOMER");
        assertThat(result.getReconciliation().getStatus()).isEqualTo("Conforme");
    }

    @Test
    void getByDaysDoesNotLoadStepAggregates() {
        when(batchRepo.findByDays(7)).thenReturn(List.of(job(10L, "FAILED", "failed")));
        when(statsRepo.findByTable("", 1)).thenReturn(List.of());

        JobExecutionDto result = jobService.getByDays(7).get(0);

        assertThat(result.getTotalReadCount()).isZero();
        assertThat(result.getTotalWriteCount()).isZero();
        assertThat(result.getThroughputPerSecond()).isNull();
        assertThat(result.isFailed()).isTrue();
        verify(batchRepo, never()).findStepsByJobExecutionId(10L);
    }

    @Test
    void getByIdThrowsNotFoundWhenJobDoesNotExist() {
        when(batchRepo.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.getById(404L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    @Test
    void getStepsChecksJobExistenceAndMapsNullCountersToZero() {
        when(batchRepo.findById(42L)).thenReturn(Optional.of(job(42L, "COMPLETED", null)));
        when(batchRepo.findStepsByJobExecutionId(42L)).thenReturn(List.of(
                BatchStepExecution.builder()
                        .stepExecutionId(5L)
                        .jobExecutionId(42L)
                        .stepName("load")
                        .status("COMPLETED")
                        .exitCode("COMPLETED")
                        .startTime(LocalDateTime.of(2026, 7, 28, 8, 0))
                        .endTime(LocalDateTime.of(2026, 7, 28, 8, 1))
                        .writeCount(120L)
                        .build()
        ));

        StepExecutionDto result = jobService.getSteps(42L).get(0);

        assertThat(result.getStepExecutionId()).isEqualTo(5L);
        assertThat(result.getReadCount()).isZero();
        assertThat(result.getWriteCount()).isEqualTo(120L);
        assertThat(result.getCommitCount()).isZero();
        assertThat(result.getThroughputPerSecond()).isEqualTo(2.0);
    }

    private static BatchJobExecution job(Long id, String status, String exitMessage) {
        return BatchJobExecution.builder()
                .jobExecutionId(id)
                .jobInstanceId(100L + id)
                .jobName("T24")
                .status(status)
                .exitCode(status)
                .exitMessage(exitMessage)
                .startTime(LocalDateTime.of(2026, 7, 28, 8, 0))
                .endTime(LocalDateTime.of(2026, 7, 28, 8, 0, 10))
                .createTime(LocalDateTime.of(2026, 7, 28, 7, 59))
                .build();
    }

    private static BatchStepExecution step(
            Long stepId,
            Long jobId,
            Long readCount,
            Long writeCount,
            Long readSkipCount,
            Long processSkipCount,
            Long writeSkipCount
    ) {
        return BatchStepExecution.builder()
                .stepExecutionId(stepId)
                .jobExecutionId(jobId)
                .stepName("step-" + stepId)
                .status("COMPLETED")
                .readCount(readCount)
                .writeCount(writeCount)
                .readSkipCount(readSkipCount)
                .processSkipCount(processSkipCount)
                .writeSkipCount(writeSkipCount)
                .build();
    }
}
