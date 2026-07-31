package wevioo.se.monitoringms.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaAuditRecordTest {

    @Test
    void anEnabledTransitionIsReportedAsActivation() {
        SchemaAuditRecord audit = SchemaAuditRecord.builder()
                .oldSourceEnabled(0).newSourceEnabled(1).build();

        assertThat(audit.wasActivated()).isTrue();
        assertThat(audit.wasDeactivated()).isFalse();
    }

    @Test
    void aDisabledTransitionIsReportedAsDeactivation() {
        SchemaAuditRecord audit = SchemaAuditRecord.builder()
                .oldSourceEnabled(1).newSourceEnabled(0).build();

        assertThat(audit.wasDeactivated()).isTrue();
        assertThat(audit.wasActivated()).isFalse();
    }

    @Test
    void anUnchangedFlagIsNeitherActivationNorDeactivation() {
        SchemaAuditRecord unchanged = SchemaAuditRecord.builder()
                .oldSourceEnabled(1).newSourceEnabled(1).build();

        assertThat(unchanged.wasActivated()).isFalse();
        assertThat(unchanged.wasDeactivated()).isFalse();
    }

    @Test
    void anIndexTransitionIsReported() {
        assertThat(SchemaAuditRecord.builder()
                .oldSourceIndexed(0).newSourceIndexed(1).build().wasIndexed()).isTrue();
        assertThat(SchemaAuditRecord.builder()
                .oldSourceIndexed(1).newSourceIndexed(0).build().wasIndexed()).isFalse();
    }
}
