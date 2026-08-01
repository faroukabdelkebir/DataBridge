package com.t24extractor.decider;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.t24extractor.support.BatchTestContext;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

class ExecutionFlowDeciderTest {

    @Test
    void exitsWhenThereIsNoData() {
        BatchTestContext context = BatchTestContext.create();
        context.jobExecution().getExecutionContext().putInt("COUNT", 0);

        FlowExecutionStatus result = new ExecutionFlowDecider()
                .decide(context.jobExecution(), context.stepExecution());

        assertEquals("EXIT", result.getName());
    }

    @Test
    void selectsBlobIncrementalManager() {
        JobParameters parameters = new JobParametersBuilder()
                .addString("Type", "B")
                .addString("Chargement", "Incremental-id")
                .toJobParameters();
        BatchTestContext context = BatchTestContext.create(parameters);
        context.jobExecution().getExecutionContext().putInt("COUNT", 1);

        FlowExecutionStatus result = new ExecutionFlowDecider()
                .decide(context.jobExecution(), context.stepExecution());

        assertEquals("BLOB-Incremental-ID-Manager", result.getName());
    }

    @Test
    void selectsPlainManagerForXmlFullLoad() {
        JobParameters parameters = new JobParametersBuilder()
                .addString("Type", "X")
                .addString("Chargement", "Full")
                .toJobParameters();
        BatchTestContext context = BatchTestContext.create(parameters);
        context.jobExecution().getExecutionContext().putInt("COUNT", 1);

        assertEquals("Manager",
                new ExecutionFlowDecider().decide(context.jobExecution(), context.stepExecution()).getName());
    }

    @Test
    void selectsIncrementalManagerWithoutBlobPrefix() {
        JobParameters parameters = new JobParametersBuilder()
                .addString("Type", "X")
                .addString("Chargement", "Incremental-id")
                .toJobParameters();
        BatchTestContext context = BatchTestContext.create(parameters);
        context.jobExecution().getExecutionContext().putInt("COUNT", 1);

        assertEquals("Incremental-ID-Manager",
                new ExecutionFlowDecider().decide(context.jobExecution(), context.stepExecution()).getName());
    }

    @Test
    void missingParametersDefaultToThePlainManager() {
        BatchTestContext context = BatchTestContext.create();
        context.jobExecution().getExecutionContext().putInt("COUNT", 5);

        // Ni Type ni Chargement fournis : le décideur ne doit pas échouer.
        assertEquals("Manager",
                new ExecutionFlowDecider().decide(context.jobExecution(), context.stepExecution()).getName());
    }

    @Test
    void executionContextTypeOverridesParameter() {
        JobParameters parameters = new JobParametersBuilder()
                .addString("Type", "X")
                .addString("Chargement", "Full")
                .toJobParameters();
        BatchTestContext context = BatchTestContext.create(parameters);
        context.jobExecution().getExecutionContext().putInt("COUNT", 1);
        context.jobExecution().getExecutionContext().putString("TYPE", "C");

        assertEquals("BLOB-Manager",
                new ExecutionFlowDecider().decide(context.jobExecution(), context.stepExecution()).getName());
    }
}
