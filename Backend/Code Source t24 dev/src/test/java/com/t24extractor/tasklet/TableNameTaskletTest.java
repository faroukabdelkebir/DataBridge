package com.t24extractor.tasklet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.t24extractor.model.TafjVoc;
import com.t24extractor.repository.t24_repo.TafjVocRepository;
import com.t24extractor.support.BatchTestContext;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

class TableNameTaskletTest {

    private final TafjVocRepository repository = mock(TafjVocRepository.class);
    private final FileControlServiceImpl fileControlService = mock(FileControlServiceImpl.class);

    @Test
    void resolvesOracleTableAndTypeIntoExecutionContext() throws Exception {
        TableNameTasklet tasklet = new TableNameTasklet(repository, fileControlService);
        tasklet.setT24TableName("CUSTOMER");
        when(fileControlService.classification("CUSTOMER")).thenReturn("FBNK");
        when(repository.findTafjVocByRecid("FBNK.CUSTOMER"))
                .thenReturn(Optional.of(new TafjVoc("FBNK.CUSTOMER", "X", "FBNK_CUSTOMER")));
        BatchTestContext context = BatchTestContext.create();

        RepeatStatus status = tasklet.execute(context.contribution(), context.chunkContext());

        assertEquals(RepeatStatus.FINISHED, status);
        assertEquals("FBNK_CUSTOMER", context.jobExecution().getExecutionContext().getString("Table_Name"));
        assertEquals('X', context.jobExecution().getExecutionContext().get("Type"));
        verify(fileControlService).classification("CUSTOMER");
    }

    @Test
    void failsWhenTafjMappingIsMissing() {
        TableNameTasklet tasklet = new TableNameTasklet(repository, fileControlService);
        tasklet.setT24TableName("CUSTOMER");
        when(fileControlService.classification("CUSTOMER")).thenReturn("FBNK");
        when(repository.findTafjVocByRecid("FBNK.CUSTOMER")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> tasklet.execute(BatchTestContext.create().contribution(), BatchTestContext.create().chunkContext()));
    }
}
