package wevioo.se.monitoringms.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wevioo.se.monitoringms.dto.AlertDto;
import wevioo.se.monitoringms.dto.DailyVolumeDto;
import wevioo.se.monitoringms.dto.KpiDto;
import wevioo.se.monitoringms.dto.ReconciliationDto;
import wevioo.se.monitoringms.dto.SlaStatusDto;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private KpiService kpiService;

    @Mock
    private ReconciliationService reconciliationService;

    @Mock
    private SlaService slaService;

    @Mock
    private AlertService alertService;

    @InjectMocks
    private ReportService reportService;

    @Test
    void generateDailyReportBuildsDegradedStatusWhenFailuresExist() {
        KpiDto kpis1d = KpiDto.builder().overallSuccessRate(100.0).failureCount(1).build();
        KpiDto kpis7d = KpiDto.builder().overallSuccessRate(95.0).failureCount(0).build();
        when(kpiService.computeKpis(1)).thenReturn(kpis1d);
        when(kpiService.computeKpis(7)).thenReturn(kpis7d);
        when(reconciliationService.getLatestReconciliation()).thenReturn(List.of(ReconciliationDto.builder().tableName("CUSTOMER").build()));
        when(slaService.getAllSlaStatuses()).thenReturn(List.of(SlaStatusDto.builder().jobName("T24").build()));
        when(alertService.getActiveAlerts()).thenReturn(List.of(
                AlertDto.builder().type("JOB_FAILURE").severity("WARNING").jobName("T24").message("failed").build()
        ));

        ReportService.DailyReport result = reportService.generateDailyReport();

        assertThat(result.getOverallStatus()).isEqualTo("Dégradé");
        assertThat(result.getKpis1Day()).isSameAs(kpis1d);
        assertThat(result.getKpis7Days()).isSameAs(kpis7d);
        assertThat(result.getReconciliations()).hasSize(1);
        assertThat(result.getSlaStatuses()).hasSize(1);
        assertThat(result.getActiveAlerts()).singleElement().satisfies(alert -> {
            assertThat(alert.getType()).isEqualTo("JOB_FAILURE");
            assertThat(alert.getSeverity()).isEqualTo("WARNING");
        });
        assertThat(result.getReportDate()).isNotNull();
        assertThat(result.getGeneratedAt()).isNotNull();
    }

    @Test
    void generateWeeklyReportComputesDeltaAndTopVolumeTable() {
        KpiDto kpis7d = KpiDto.builder()
                .overallSuccessRate(90.0)
                .rowsByTable(Map.of("CUSTOMER", 200L, "FT_HIS", 500L))
                .volumeTrend(List.of(DailyVolumeDto.builder().date("20260728").rowsWritten(500).build()))
                .build();
        KpiDto kpis14d = KpiDto.builder().overallSuccessRate(80.0).build();
        when(kpiService.computeKpis(7)).thenReturn(kpis7d);
        when(kpiService.computeKpis(14)).thenReturn(kpis14d);

        ReportService.WeeklyReport result = reportService.generateWeeklyReport();

        assertThat(result.getSuccessRateDeltaVsPreviousWeek()).isEqualTo(20.0);
        assertThat(result.getTopVolumeTable()).isEqualTo("FT_HIS");
        assertThat(result.getTrend()).hasSize(1);
        assertThat(result.getWeekStart()).isNotNull();
        assertThat(result.getWeekEnd()).isNotNull();
    }
}
