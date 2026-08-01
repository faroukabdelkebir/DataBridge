package com.t24extractor.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.t24extractor.exception.DataNotFoundException;
import com.t24extractor.model.SchemaSource;
import com.t24extractor.model.UpdateSchemaSourceEnabledRequest;
import com.t24extractor.service.ISchemaSourceService;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Délégation seule : les règles de mise à jour sont couvertes par
 * SchemaSourceServiceImplTest.
 */
class SchemaSourceControllerTest {

    private final ISchemaSourceService service = mock(ISchemaSourceService.class);
    private final SchemaSourceController controller = new SchemaSourceController(service);

    @Test
    void returnsColumnsFromTheService() {
        List<SchemaSource> columns = List.of(schema("NAME"));
        when(service.findColumnsByTable("CUSTOMER")).thenReturn(columns);

        assertSame(columns, controller.getColumnsByTable("CUSTOMER"));
    }

    @Test
    void unwrapsTheEnabledFlagFromTheRequestBody() throws Exception {
        SchemaSource column = schema("NAME");
        when(service.updateColumnEnabled("CUSTOMER", "NAME", false)).thenReturn(column);

        assertSame(column, controller.updateColumnEnabled(
                "CUSTOMER", "NAME", new UpdateSchemaSourceEnabledRequest(false)));
        verify(service).updateColumnEnabled("CUSTOMER", "NAME", false);
    }

    @Test
    void propagatesNotFoundFromTheService() throws Exception {
        when(service.updateColumnEnabled("CUSTOMER", "missing", true))
                .thenThrow(new DataNotFoundException("Schema source column not found: CUSTOMER.missing"));

        // DataNotFoundException porte @ResponseStatus(NOT_FOUND) : le contrôleur
        // la laisse remonter plutôt que de construire lui-même la réponse.
        assertThrows(DataNotFoundException.class, () -> controller.updateColumnEnabled(
                "CUSTOMER", "missing", new UpdateSchemaSourceEnabledRequest(true)));
    }

    private SchemaSource schema(String column) {
        return new SchemaSource("id-" + column, "CUSTOMER", column, "VARCHAR",
                true, false, "tag", "S", false);
    }
}
