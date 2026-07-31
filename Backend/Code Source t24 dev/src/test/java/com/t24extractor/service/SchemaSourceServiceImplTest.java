package com.t24extractor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.t24extractor.exception.DataNotFoundException;
import com.t24extractor.model.SchemaSource;
import com.t24extractor.repository.bi_repos.SchemaSourceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SchemaSourceServiceImplTest {

    private final SchemaSourceRepository repository = mock(SchemaSourceRepository.class);
    private final SchemaSourceServiceImpl service = new SchemaSourceServiceImpl(repository);

    @Test
    void returnsColumnsByTableName() {
        SchemaSource column = schema("NAME", true);
        when(repository.findAllByTableName("CUSTOMER")).thenReturn(List.of(column));

        assertEquals(List.of(column), service.findColumnsByTable("CUSTOMER"));
    }

    @Test
    void returnsEmptyListForUnknownTable() {
        when(repository.findAllByTableName("INCONNUE")).thenReturn(List.of());

        assertEquals(List.of(), service.findColumnsByTable("INCONNUE"));
    }

    @Test
    void updatesColumnEnabledAndReturnsPersistedState() throws Exception {
        SchemaSource column = schema("NAME", false);
        when(repository.updateEnabledByTableNameAndColumnName("CUSTOMER", "NAME", false)).thenReturn(1);
        when(repository.findAllByTableNameAndColumnName("CUSTOMER", "NAME")).thenReturn(Optional.of(column));

        assertEquals(column, service.updateColumnEnabled("CUSTOMER", "NAME", false));
        verify(repository).updateEnabledByTableNameAndColumnName("CUSTOMER", "NAME", false);
    }

    @Test
    void throwsWhenColumnDoesNotExist() {
        when(repository.updateEnabledByTableNameAndColumnName("CUSTOMER", "missing", true)).thenReturn(0);

        DataNotFoundException error = assertThrows(DataNotFoundException.class,
                () -> service.updateColumnEnabled("CUSTOMER", "missing", true));

        assertEquals("Schema source column not found: CUSTOMER.missing", error.getMessage());
        // Aucune relecture inutile lorsque la mise à jour n'a touché aucune ligne.
        verify(repository, never()).findAllByTableNameAndColumnName("CUSTOMER", "missing");
    }

    @Test
    void throwsWhenUpdatedColumnCannotBeReadBack() {
        when(repository.updateEnabledByTableNameAndColumnName("CUSTOMER", "NAME", true)).thenReturn(1);
        when(repository.findAllByTableNameAndColumnName("CUSTOMER", "NAME")).thenReturn(Optional.empty());

        // La mise à jour a bien touché une ligne mais la relecture échoue :
        // on refuse de renvoyer un corps vide au frontend.
        assertThrows(DataNotFoundException.class,
                () -> service.updateColumnEnabled("CUSTOMER", "NAME", true));
    }

    private SchemaSource schema(String column, boolean enabled) {
        return new SchemaSource("id-" + column, "CUSTOMER", column, "VARCHAR",
                enabled, false, "tag", "S", false);
    }
}
