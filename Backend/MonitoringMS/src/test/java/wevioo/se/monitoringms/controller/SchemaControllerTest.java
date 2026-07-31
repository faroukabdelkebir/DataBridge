package wevioo.se.monitoringms.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wevioo.se.monitoringms.config.CacheConfig;
import wevioo.se.monitoringms.model.SchemaAuditRecord;
import wevioo.se.monitoringms.model.SchemaSourceRecord;
import wevioo.se.monitoringms.repository.SchemaRepository;
import wevioo.se.monitoringms.service.SchemaService;

import java.net.URI;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SchemaController.class)
@Import(CacheConfig.class)
class SchemaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SchemaService schemaService;

    @Test
    void getSummaryExposesTheColumnCountsPerTable() throws Exception {
        when(schemaService.getSchemaSummary()).thenReturn(List.of(
                new SchemaRepository.TableSchemaSummary("CUSTOMER", 250, 42, 5, 3)));

        mockMvc.perform(get("/api/v1/schema/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tableName").value("CUSTOMER"))
                .andExpect(jsonPath("$[0].totalColumns").value(250))
                .andExpect(jsonPath("$[0].enabledColumns").value(42))
                .andExpect(jsonPath("$[0].indexedColumns").value(5))
                .andExpect(jsonPath("$[0].mvColumns").value(3));
    }

    @Test
    void getAllColumnsReturnsTheDictionary() throws Exception {
        when(schemaService.getAllColumns()).thenReturn(List.of(sampleColumn()));

        mockMvc.perform(get("/api/v1/schema/columns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].columnName").value("MNEMONIC"))
                .andExpect(jsonPath("$[0].dataType").value("VARCHAR"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[0].singleMult").value("S"));
    }

    @Test
    void getColumnsByTableForwardsTheTableName() throws Exception {
        when(schemaService.getColumnsByTable("CUSTOMER")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/schema/columns/CUSTOMER"))
                .andExpect(status().isOk());

        verify(schemaService).getColumnsByTable("CUSTOMER");
    }

    /**
     * Les tables T24 historisées contiennent un « # » (FUNDS_TRANSFER#HIS), qui
     * délimite le fragment d'une URL : les clients doivent l'encoder en %23,
     * faute de quoi seule la partie gauche du nom parvient au contrôleur.
     */
    @Test
    void getColumnsByTableRequiresAnEncodedHashInT24TableNames() throws Exception {
        when(schemaService.getColumnsByTable("FUNDS_TRANSFER#HIS")).thenReturn(List.of(sampleColumn()));

        mockMvc.perform(get(URI.create("/api/v1/schema/columns/FUNDS_TRANSFER%23HIS")))
                .andExpect(status().isOk());

        verify(schemaService).getColumnsByTable("FUNDS_TRANSFER#HIS");
    }

    @Test
    void getRecentAuditDefaultsToFiftyEvents() throws Exception {
        when(schemaService.getRecentAuditEvents(50)).thenReturn(List.of(SchemaAuditRecord.builder()
                .id(1)
                .sourceTableName("CUSTOMER")
                .sourceColumnName("GENDER")
                .oldSourceEnabled(0)
                .newSourceEnabled(1)
                .updatedBy("T24")
                .updatedOn(20260728L)
                .build()));

        mockMvc.perform(get("/api/v1/schema/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceColumnName").value("GENDER"))
                .andExpect(jsonPath("$[0].newSourceEnabled").value(1));

        verify(schemaService).getRecentAuditEvents(50);
    }

    @Test
    void getRecentAuditHonoursAnExplicitLimit() throws Exception {
        when(schemaService.getRecentAuditEvents(5)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/schema/audit").param("limit", "5"))
                .andExpect(status().isOk());

        verify(schemaService).getRecentAuditEvents(5);
    }

    @Test
    void getAuditByTableForwardsTheTableName() throws Exception {
        when(schemaService.getAuditByTable("CUSTOMER")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/schema/audit/CUSTOMER"))
                .andExpect(status().isOk());

        verify(schemaService).getAuditByTable("CUSTOMER");
    }

    private static SchemaSourceRecord sampleColumn() {
        return SchemaSourceRecord.builder()
                .id("CUSTOMER.MNEMONIC")
                .tableName("CUSTOMER")
                .columnName("MNEMONIC")
                .dataType("VARCHAR")
                .enabled(true)
                .indexed(false)
                .ssTag("1")
                .singleMult("S")
                .localRef(false)
                .insertDate(20260728L)
                .build();
    }
}
