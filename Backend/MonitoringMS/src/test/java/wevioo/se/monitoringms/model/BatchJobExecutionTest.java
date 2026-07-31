package wevioo.se.monitoringms.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BatchJobExecutionTest {

    @Test
    void durationIsComputedBetweenStartAndEnd() {
        BatchJobExecution job = BatchJobExecution.builder()
                .startTime(LocalDateTime.of(2026, 7, 28, 6, 0, 0))
                .endTime(LocalDateTime.of(2026, 7, 28, 6, 25, 30))
                .build();

        assertThat(job.getDurationSeconds()).isEqualTo(1530L);
    }

    @Test
    void durationOfARunningJobIsMeasuredUntilNow() {
        BatchJobExecution job = BatchJobExecution.builder()
                .startTime(LocalDateTime.now().minusSeconds(120))
                .status("STARTED")
                .build();

        assertThat(job.getDurationSeconds()).isBetween(119L, 130L);
    }

    @Test
    void durationIsNullWhenTheJobNeverStarted() {
        assertThat(BatchJobExecution.builder().status("STARTING").build().getDurationSeconds()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"STARTED", "STARTING"})
    void runningStatusesAreDetected(String status) {
        assertThat(BatchJobExecution.builder().status(status).build().isRunning()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"COMPLETED", "FAILED", "STOPPED", "ABANDONED", "UNKNOWN", "started"})
    void nonRunningStatusesAreNotFlaggedAsRunning(String status) {
        assertThat(BatchJobExecution.builder().status(status).build().isRunning()).isFalse();
    }

    @Test
    void failedStatusIsDetected() {
        assertThat(BatchJobExecution.builder().status("FAILED").build().isFailed()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"COMPLETED", "STARTED", "ABANDONED", "failed"})
    void otherStatusesAreNotFlaggedAsFailed(String status) {
        assertThat(BatchJobExecution.builder().status(status).build().isFailed()).isFalse();
    }
}