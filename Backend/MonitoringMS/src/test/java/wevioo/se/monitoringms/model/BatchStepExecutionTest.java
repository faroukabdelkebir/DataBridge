package wevioo.se.monitoringms.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BatchStepExecutionTest {

    @Test
    void totalSkipCountSumsTheThreeSkipCounters() {
        BatchStepExecution step = BatchStepExecution.builder()
                .readSkipCount(3L)
                .processSkipCount(4L)
                .writeSkipCount(5L)
                .build();

        assertThat(step.getTotalSkipCount()).isEqualTo(12L);
    }

    @Test
    void totalSkipCountTreatsMissingCountersAsZero() {
        BatchStepExecution step = BatchStepExecution.builder().readSkipCount(7L).build();

        assertThat(step.getTotalSkipCount()).isEqualTo(7L);
        assertThat(BatchStepExecution.builder().build().getTotalSkipCount()).isZero();
    }

    @Test
    void throughputIsWrittenItemsPerSecond() {
        BatchStepExecution step = BatchStepExecution.builder()
                .startTime(LocalDateTime.of(2026, 7, 28, 6, 0, 0))
                .endTime(LocalDateTime.of(2026, 7, 28, 6, 0, 10))
                .writeCount(2500L)
                .build();

        assertThat(step.getThroughputPerSecond()).isEqualTo(250.0);
    }

    @Test
    void throughputIsNullWhenTheStepNeverStarted() {
        assertThat(BatchStepExecution.builder().writeCount(100L).build().getThroughputPerSecond()).isNull();
    }

    @Test
    void throughputIsNullWhenNothingWasWritten() {
        BatchStepExecution step = BatchStepExecution.builder()
                .startTime(LocalDateTime.of(2026, 7, 28, 6, 0, 0))
                .endTime(LocalDateTime.of(2026, 7, 28, 6, 0, 10))
                .build();

        assertThat(step.getThroughputPerSecond()).isNull();
    }

    @Test
    void throughputIsNullForASubSecondStepToAvoidDivisionByZero() {
        LocalDateTime instant = LocalDateTime.of(2026, 7, 28, 6, 0, 0);
        BatchStepExecution step = BatchStepExecution.builder()
                .startTime(instant)
                .endTime(instant)
                .writeCount(50L)
                .build();

        assertThat(step.getDurationSeconds()).isZero();
        assertThat(step.getThroughputPerSecond()).isNull();
    }

    @Test
    void durationOfARunningStepIsMeasuredUntilNow() {
        BatchStepExecution step = BatchStepExecution.builder()
                .startTime(LocalDateTime.now().minusSeconds(60))
                .build();

        assertThat(step.getDurationSeconds()).isBetween(59L, 70L);
    }
}
