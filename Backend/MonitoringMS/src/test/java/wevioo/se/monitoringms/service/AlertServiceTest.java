package wevioo.se.monitoringms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import wevioo.se.monitoringms.dto.AlertDto;
import wevioo.se.monitoringms.dto.ReconciliationDto;
import wevioo.se.monitoringms.dto.SlaStatusDto;
import wevioo.se.monitoringms.repository.BatchExecutionRepository;
import wevioo.se.monitoringms.repository.SchemaRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private BatchExecutionRepository batchRepo;

    @Mock
    private ReconciliationService reconciliationService;

    @Mock
    private SlaService slaService;

    @Mock
    private SchemaRepository schemaRepo;

    private AlertService alertService;

    @BeforeEach
    void setUp() {
        alertService = new AlertService(batchRepo, reconciliationService, slaService, schemaRepo);
        ReflectionTestUtils.setField(alertService, "consecutiveFailureThreshold", 3);
    }

    @Test
    void evaluateAlertsCreatesCriticalConsecutiveFailureAlertAtThreshold() {
        when(batchRepo.countConsecutiveFailures("T24")).thenReturn(3);
        when(batchRepo.countConsecutiveFailures("CUSTOMER.JOB")).thenReturn(0);
        when(batchRepo.countConsecutiveFailures("AA.BILL.DETAILS.JOB")).thenReturn(0);
        when(batchRepo.countConsecutiveFailures("FT.HIS.JOB")).thenReturn(0);
        when(slaService.getAllSlaStatuses()).thenReturn(List.of());
        when(reconciliationService.getLatestReconciliation()).thenReturn(List.of());
        when(schemaRepo.countRecentDrifts(1)).thenReturn(0);

        alertService.evaluateAlerts();

        assertThat(alertService.getActiveAlerts())
                .singleElement()
                .satisfies(alert -> {
                    assertThat(alert.getAlertKey()).isEqualTo("CONSECUTIVE_FAILURE_T24");
                    assertThat(alert.getSeverity()).isEqualTo("CRITICAL");
                    assertThat(alert.getMessageKey()).isEqualTo("alerts.msg.consecutiveFailure");
                    assertThat(alert.getParams()).containsEntry("count", 3);
                });
    }

    @Test
    void evaluateAlertsDowngradesSingleFailureAndClearsItAfterSuccess() {
        when(batchRepo.countConsecutiveFailures("T24")).thenReturn(1, 0);
        when(batchRepo.countConsecutiveFailures("CUSTOMER.JOB")).thenReturn(0);
        when(batchRepo.countConsecutiveFailures("AA.BILL.DETAILS.JOB")).thenReturn(0);
        when(batchRepo.countConsecutiveFailures("FT.HIS.JOB")).thenReturn(0);
        when(slaService.getAllSlaStatuses()).thenReturn(List.of());
        when(reconciliationService.getLatestReconciliation()).thenReturn(List.of());
        when(schemaRepo.countRecentDrifts(1)).thenReturn(0);

        alertService.evaluateAlerts();

        assertThat(alertService.getActiveAlerts())
                .extracting(AlertDto::getAlertKey)
                .containsExactly("JOB_FAILURE_T24");

        alertService.evaluateAlerts();

        assertThat(alertService.getActiveAlerts()).isEmpty();
    }

    @Test
    void evaluateAlertsCreatesSlaReconciliationAndSchemaAlerts() {
        when(batchRepo.countConsecutiveFailures("T24")).thenReturn(0);
        when(batchRepo.countConsecutiveFailures("CUSTOMER.JOB")).thenReturn(0);
        when(batchRepo.countConsecutiveFailures("AA.BILL.DETAILS.JOB")).thenReturn(0);
        when(batchRepo.countConsecutiveFailures("FT.HIS.JOB")).thenReturn(0);
        when(slaService.getAllSlaStatuses()).thenReturn(List.of(
                SlaStatusDto.builder()
                        .jobName("T24")
                        .slaBreached(true)
                        .lateBySeconds(3660)
                        .build()
        ));
        when(reconciliationService.getLatestReconciliation()).thenReturn(List.of(
                ReconciliationDto.builder()
                        .tableName("CUSTOMER")
                        .oracleCount(100)
                        .sqlServerCount(70)
                        .reconciliationRate(70.0)
                        .status("Non conforme")
                        .build(),
                ReconciliationDto.builder()
                        .tableName("FT_HIS")
                        .reconciliationRate(92.4)
                        .status("Avertissement")
                        .build()
        ));
        when(schemaRepo.countRecentDrifts(1)).thenReturn(2);

        alertService.evaluateAlerts();

        Map<String, AlertDto> alertsByKey = alertService.getActiveAlerts().stream()
                .collect(java.util.stream.Collectors.toMap(AlertDto::getAlertKey, alert -> alert));

        assertThat(alertsByKey).containsOnlyKeys(
                "SLA_BREACH_T24",
                "RECONCILIATION_KO_CUSTOMER",
                "RECONCILIATION_KO_FT_HIS",
                "SCHEMA_DRIFT"
        );
        assertThat(alertsByKey.get("SLA_BREACH_T24").getSeverity()).isEqualTo("CRITICAL");
        assertThat(alertsByKey.get("RECONCILIATION_KO_CUSTOMER").getSeverity()).isEqualTo("CRITICAL");
        assertThat(alertsByKey.get("RECONCILIATION_KO_CUSTOMER").getParams()).containsEntry("rate", "70.0");
        assertThat(alertsByKey.get("RECONCILIATION_KO_FT_HIS").getSeverity()).isEqualTo("WARNING");
        assertThat(alertsByKey.get("SCHEMA_DRIFT").getParams()).containsEntry("count", 2);
    }

    @Test
    void resolveAlertRemovesActiveAlert() {
        when(batchRepo.countConsecutiveFailures("T24")).thenReturn(1);
        when(batchRepo.countConsecutiveFailures("CUSTOMER.JOB")).thenReturn(0);
        when(batchRepo.countConsecutiveFailures("AA.BILL.DETAILS.JOB")).thenReturn(0);
        when(batchRepo.countConsecutiveFailures("FT.HIS.JOB")).thenReturn(0);
        when(slaService.getAllSlaStatuses()).thenReturn(List.of());
        when(reconciliationService.getLatestReconciliation()).thenReturn(List.of());
        when(schemaRepo.countRecentDrifts(1)).thenReturn(0);
        alertService.evaluateAlerts();

        alertService.resolveAlert("JOB_FAILURE_T24");

        assertThat(alertService.getActiveAlerts()).isEmpty();
    }
}
