package com.t24extractor.controller;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

class JobLauncherControllerTest {

    private final JobOperator operator = mock(JobOperator.class);
    private final JobRepository repository = mock(JobRepository.class);
    private final Job loader = job("loader");
    private final Job main = job("T24");
    private final Job customer = job("customer");
    private final Job ft = job("ft");
    private final Job bill = job("bill");
    private final JobLauncherController controller =
            new JobLauncherController(operator, repository, loader, main, customer, ft, bill);

    @Test
    void startsEachExposedJob() throws Exception {
        controller.handleMain();
        controller.handleCustomer();
        controller.handleFt();
        when(repository.findRunningJobExecutions("bill")).thenReturn(Set.of());
        controller.handleBillDetails();

        verify(operator).start(org.mockito.ArgumentMatchers.eq(main),
                org.mockito.ArgumentMatchers.any(JobParameters.class));
        verify(operator).start(org.mockito.ArgumentMatchers.eq(customer),
                org.mockito.ArgumentMatchers.any(JobParameters.class));
        verify(operator).start(org.mockito.ArgumentMatchers.eq(ft),
                org.mockito.ArgumentMatchers.any(JobParameters.class));
        verify(operator).start(org.mockito.ArgumentMatchers.eq(bill),
                org.mockito.ArgumentMatchers.any(JobParameters.class));
    }

    @Test
    void wrapsStartFailures() throws Exception {
        when(operator.start(any(Job.class), any(JobParameters.class)))
                .thenThrow(new JobExecutionAlreadyRunningException("running"));

        assertThrows(IllegalStateException.class, controller::handleMain);
    }

    @Test
    void stopsMostRecentMainExecution() throws Exception {
        JobExecution oldExecution = execution(1L, LocalDateTime.now().minusMinutes(2));
        JobExecution recentExecution = execution(2L, LocalDateTime.now());
        Set<JobExecution> executions = new LinkedHashSet<>(Set.of(oldExecution, recentExecution));
        when(repository.findRunningJobExecutions("T24")).thenReturn(executions);

        controller.handleStoppingAllJobs();

        verify(operator).stop(recentExecution);
        verify(operator, never()).stop(oldExecution);
    }

    @Test
    void wrapsStartFailuresOnEveryPerTableEndpoint() throws Exception {
        when(operator.start(any(Job.class), any(JobParameters.class)))
                .thenThrow(new JobExecutionAlreadyRunningException("running"));

        assertThrows(IllegalStateException.class, controller::handleCustomer);
        assertThrows(IllegalStateException.class, controller::handleFt);
    }

    @Test
    void billDetailsStartsEvenWhenAnotherRunIsAlreadyInProgress() throws Exception {
        when(repository.findRunningJobExecutions("bill"))
                .thenReturn(Set.of(execution(9L, LocalDateTime.now())));

        controller.handleBillDetails();

        // Le contrôleur se contente d'avertir : la décision de rejet appartient
        // à Spring Batch, qui refusera une instance déjà complétée.
        verify(operator).start(org.mockito.ArgumentMatchers.eq(bill),
                org.mockito.ArgumentMatchers.any(JobParameters.class));
    }

    @Test
    void stoppingWithoutAnyRunningExecutionIsANoOp() throws Exception {
        when(repository.findRunningJobExecutions("T24")).thenReturn(Set.of());

        controller.handleStoppingAllJobs();

        verify(operator, never()).stop(org.mockito.ArgumentMatchers.any(JobExecution.class));
    }

    @Test
    void reportsFailureToStopARunningExecution() throws Exception {
        JobExecution running = execution(1L, LocalDateTime.now());
        when(repository.findRunningJobExecutions("T24")).thenReturn(Set.of(running));
        when(operator.stop(running)).thenThrow(new IllegalStateException("cannot stop"));

        assertThrows(IllegalArgumentException.class, controller::handleStoppingAllJobs);
    }

    @Test
    void everyLaunchCarriesAUniqueRunId() throws Exception {
        ArgumentCaptor<JobParameters> parameters = ArgumentCaptor.forClass(JobParameters.class);

        controller.handleMain();
        controller.handleMain();

        verify(operator, org.mockito.Mockito.times(2))
                .start(org.mockito.ArgumentMatchers.eq(main), parameters.capture());
        // Sans run.id distinct, le second lancement serait rejeté comme doublon.
        assertNotEquals(
                parameters.getAllValues().get(0).getString("run.id"),
                parameters.getAllValues().get(1).getString("run.id"));
    }

    private Job job(String name) {
        Job job = mock(Job.class);
        when(job.getName()).thenReturn(name);
        return job;
    }

    private JobExecution execution(long id, LocalDateTime created) {
        JobExecution execution =
                new JobExecution(id, new JobInstance(id, "T24"), new JobParameters());
        execution.setCreateTime(created);
        return execution;
    }
}
