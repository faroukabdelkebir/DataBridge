package com.t24extractor.support;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;

public final class BatchTestContext {

    private final JobExecution jobExecution;
    private final StepExecution stepExecution;
    private final StepContribution contribution;
    private final ChunkContext chunkContext;

    private BatchTestContext(JobParameters parameters) {
        jobExecution = new JobExecution(1L, new JobInstance(1L, "testJob"), parameters);
        stepExecution = new StepExecution("testStep", jobExecution);
        contribution = new StepContribution(stepExecution);
        chunkContext = new ChunkContext(new StepContext(stepExecution));
    }

    public static BatchTestContext create() {
        return new BatchTestContext(new JobParameters());
    }

    public static BatchTestContext create(JobParameters parameters) {
        return new BatchTestContext(parameters);
    }

    public JobExecution jobExecution() {
        return jobExecution;
    }

    public StepExecution stepExecution() {
        return stepExecution;
    }

    public StepContribution contribution() {
        return contribution;
    }

    public ChunkContext chunkContext() {
        return chunkContext;
    }
}
