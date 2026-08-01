package com.t24extractor.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.jdbc.core.JdbcTemplate;

class JobListenerTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final JobListener listener = new JobListener(jdbcTemplate);

    @Test
    void doesNotAlterExecutionStatusOrTouchTheDatabase() {
        JobExecution execution = execution();
        BatchStatus statusBefore = execution.getStatus();

        listener.beforeJob(execution);
        listener.afterJob(execution);

        // Le listener est purement observationnel : il ne doit ni écrire en base
        // ni influencer l'issue du job.
        assertEquals(statusBefore, execution.getStatus());
        assertEquals(0, execution.getExecutionContext().size());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void toleratesFailedExecutions() {
        JobExecution execution = execution();
        execution.setStatus(BatchStatus.FAILED);

        listener.afterJob(execution);

        assertEquals(BatchStatus.FAILED, execution.getStatus());
        verifyNoInteractions(jdbcTemplate);
    }

    private JobExecution execution() {
        return new JobExecution(1L, new JobInstance(1L, "T24"), new JobParameters());
    }
}
