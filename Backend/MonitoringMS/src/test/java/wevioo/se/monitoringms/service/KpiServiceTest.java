package wevioo.se.monitoringms.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wevioo.se.monitoringms.dto.KpiDto;
import wevioo.se.monitoringms.repository.BatchExecutionRepository;
import wevioo.se.monitoringms.repository.StatsRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KpiServiceTest {

    @Mock
    private BatchExecutionRepository batchRepo;

    @Mock
    private StatsRepository statsRepo;

    @InjectMocks
    private KpiService kpiService;

    @Test
    void computeKpisAggregatesAndRoundsValues() {
        when(batchRepo.computeJobAggregates(7)).thenReturn(List.of(
                new BatchExecutionRepository.JobAggregate("T24", 4, 3, 1, 12.34),
                new BatchExecutionRepository.JobAggregate("CUSTOMER.JOB", 2, 2, 0, 20.0)
        ));
        when(batchRepo.computeDailyTrend(7)).thenReturn(List.of(
                new BatchExecutionRepository.DailyAggregate("20260728", 1000L, 900L, 3, 2),
                new BatchExecutionRepository.DailyAggregate("20260727", 500L, 300L, 2, 2)
        ));
        when(batchRepo.computeSuccessRate(7)).thenReturn(83.456);
        when(batchRepo.countConsecutiveFailures("T24")).thenReturn(2);
        when(batchRepo.countConsecutiveFailures("CUSTOMER.JOB")).thenReturn(0);
        when(statsRepo.sumRowsExtracted(7)).thenReturn(1200L);
        when(statsRepo.sumRowsByTable(7)).thenReturn(List.of(
                new StatsRepository.TableVolumeRecord("CUSTOMER", 700L, 720L, 2),
                new StatsRepository.TableVolumeRecord("FT_HIS", 500L, 500L, 1)
        ));

        KpiDto result = kpiService.computeKpis(7);

        assertThat(result.getWindowDays()).isEqualTo(7);
        assertThat(result.getOverallSuccessRate()).isEqualTo(83.46);
        assertThat(result.getSuccessRateByJob()).containsEntry("T24", 75.0);
        assertThat(result.getRowsByTable()).containsEntry("CUSTOMER", 700L);
        assertThat(result.getAvgThroughputPerSec()).isEqualTo(225.0);
        assertThat(result.getAvgDurationSeconds()).isEqualTo(16.2);
        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getConsecutiveFailureCount()).isEqualTo(2);
        assertThat(result.getVolumeTrend()).hasSize(2);
        assertThat(result.getVolumeTrend().get(0).getSuccessRate()).isEqualTo(66.66666666666666);
        assertThat(result.getComputedAt()).isNotNull();

        verify(batchRepo).computeJobAggregates(7);
        verify(statsRepo).sumRowsExtracted(7);
    }

    @Test
    void computeKpisReturnsEmptyMetricsWhenRepositoriesHaveNoData() {
        when(batchRepo.computeJobAggregates(30)).thenReturn(List.of());
        when(batchRepo.computeDailyTrend(30)).thenReturn(List.of());
        when(batchRepo.computeSuccessRate(30)).thenReturn(0.0);
        when(statsRepo.sumRowsExtracted(30)).thenReturn(0L);
        when(statsRepo.sumRowsByTable(30)).thenReturn(List.of());

        KpiDto result = kpiService.computeKpis(30);

        assertThat(result.getOverallSuccessRate()).isZero();
        assertThat(result.getSuccessRateByJob()).isEmpty();
        assertThat(result.getRowsByTable()).isEmpty();
        assertThat(result.getAvgThroughputPerSec()).isZero();
        assertThat(result.getAvgDurationSeconds()).isZero();
        assertThat(result.getFailureCount()).isZero();
        assertThat(result.getConsecutiveFailureCount()).isZero();
        assertThat(result.getVolumeTrend()).isEmpty();
    }
}
