package com.t24extractor.tasklet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.t24extractor.model.FileControl;
import com.t24extractor.repository.t24_repo.FileControlRepository;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FileControlServiceImplTest {

    private final FileControlRepository repository = mock(FileControlRepository.class);
    private final FileControlServiceImpl service = new FileControlServiceImpl(repository);

    @Test
    void mapsInternalClassificationToF() {
        when(repository.findFileControlByRecid("ACCOUNT"))
                .thenReturn(Optional.of(new FileControl("ACCOUNT", "", "INT")));

        assertEquals("F", service.classification("ACCOUNT$HIS"));
    }

    @Test
    void mapsOtherClassificationsToFbnk() {
        when(repository.findFileControlByRecid("CUSTOMER"))
                .thenReturn(Optional.of(new FileControl("CUSTOMER", "", "CUS")));

        assertEquals("FBNK", service.classification("CUSTOMER"));
    }

    @Test
    void failsWhenFileControlIsMissing() {
        when(repository.findFileControlByRecid("MISSING")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.classification("MISSING"));
    }
}
