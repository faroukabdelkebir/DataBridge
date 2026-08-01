package com.t24extractor.tasklet;

import static com.t24extractor.commons.Constants.COB_RECID;
import static com.t24extractor.commons.Constants.COB_RUNNING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.t24extractor.exception.DataNotFoundException;
import com.t24extractor.model.CobStatus;
import com.t24extractor.repository.t24_repo.CobRepository;
import com.t24extractor.support.BatchTestContext;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

class CobStatusTaskletTest {

    private final CobRepository repository = mock(CobRepository.class);

    @Test
    void finishesNormallyWhenCobIsStopped() throws Exception {
        when(repository.findById(COB_RECID)).thenReturn(Optional.of(new CobStatus(COB_RECID, "STOP")));
        BatchTestContext context = BatchTestContext.create();

        RepeatStatus result = new CobStatusTasklet(repository)
                .execute(context.contribution(), context.chunkContext());

        assertEquals(RepeatStatus.FINISHED, result);
    }

    @Test
    void marksStepAndJobWhenCobIsRunning() throws Exception {
        when(repository.findById(COB_RECID)).thenReturn(Optional.of(new CobStatus(COB_RECID, "RUN")));
        BatchTestContext context = BatchTestContext.create();

        new CobStatusTasklet(repository).execute(context.contribution(), context.chunkContext());

        assertEquals(COB_RUNNING, context.contribution().getExitStatus().getExitCode());
        assertEquals(COB_RUNNING, context.jobExecution().getExitStatus().getExitCode());
    }

    @Test
    void failsWhenCobStatusIsMissing() {
        when(repository.findById(COB_RECID)).thenReturn(Optional.empty());
        BatchTestContext context = BatchTestContext.create();

        assertThrows(DataNotFoundException.class,
                () -> new CobStatusTasklet(repository).execute(context.contribution(), context.chunkContext()));
    }
}
