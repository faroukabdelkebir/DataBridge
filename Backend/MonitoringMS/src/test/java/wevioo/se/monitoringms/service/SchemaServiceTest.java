package wevioo.se.monitoringms.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wevioo.se.monitoringms.model.SchemaAuditRecord;
import wevioo.se.monitoringms.model.SchemaSourceRecord;
import wevioo.se.monitoringms.repository.SchemaRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchemaServiceTest {

    @Mock
    private SchemaRepository schemaRepo;

    @InjectMocks
    private SchemaService schemaService;

    @Test
    void delegatesSchemaSourceQueriesToRepository() {
        List<SchemaSourceRecord> columns = List.of(SchemaSourceRecord.builder().tableName("CUSTOMER").columnName("ID").build());
        when(schemaRepo.findAll()).thenReturn(columns);
        when(schemaRepo.findByTable("CUSTOMER")).thenReturn(columns);
        when(schemaRepo.summarizeByTable()).thenReturn(List.of(
                new SchemaRepository.TableSchemaSummary("CUSTOMER", 10, 8, 3, 1)
        ));

        assertThat(schemaService.getAllColumns()).isSameAs(columns);
        assertThat(schemaService.getColumnsByTable("CUSTOMER")).isSameAs(columns);
        assertThat(schemaService.getSchemaSummary()).singleElement()
                .extracting(SchemaRepository.TableSchemaSummary::tableName)
                .isEqualTo("CUSTOMER");
    }

    @Test
    void delegatesAuditAndDriftQueriesToRepository() {
        List<SchemaAuditRecord> audits = List.of(SchemaAuditRecord.builder().id(1).sourceTableName("CUSTOMER").build());
        when(schemaRepo.findRecentAudit(5)).thenReturn(audits);
        when(schemaRepo.findAuditByTable("CUSTOMER")).thenReturn(audits);
        when(schemaRepo.countRecentDrifts(1)).thenReturn(2);

        assertThat(schemaService.getRecentAuditEvents(5)).isSameAs(audits);
        assertThat(schemaService.getAuditByTable("CUSTOMER")).isSameAs(audits);
        assertThat(schemaService.countRecentDrifts(1)).isEqualTo(2);

        verify(schemaRepo).findRecentAudit(5);
        verify(schemaRepo).findAuditByTable("CUSTOMER");
        verify(schemaRepo).countRecentDrifts(1);
    }
}
