package wevioo.se.monitoringms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import wevioo.se.monitoringms.dto.ReconciliationDto;
import wevioo.se.monitoringms.model.StatsRecord;
import wevioo.se.monitoringms.repository.StatsRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private StatsRepository statsRepo;

    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new ReconciliationService(statsRepo);
        ReflectionTestUtils.setField(reconciliationService, "tolerancePct", 5.0);
    }

    @Test
    void getLatestReconciliationClassifiesConformeWarningAndNonConforme() {
        when(statsRepo.findLatestPerTable()).thenReturn(List.of(
                stats("CUSTOMER", 100, 96),
                stats("ACCOUNT", 100, 90),
                stats("FT_HIS", 100, 70)
        ));

        List<ReconciliationDto> result = reconciliationService.getLatestReconciliation();

        assertThat(result).extracting(ReconciliationDto::getStatus)
                .containsExactly("Conforme", "Avertissement", "Non conforme");
        assertThat(result.get(0).isReconciled()).isTrue();
        assertThat(result.get(1).isReconciled()).isFalse();
        assertThat(result.get(2).getDelta()).isEqualTo(30);
    }

    @Test
    void getLatestReconciliationTreatsZeroOracleCountAsFullyReconciled() {
        when(statsRepo.findLatestPerTable()).thenReturn(List.of(stats("EMPTY_TABLE", 0, 0)));

        ReconciliationDto result = reconciliationService.getLatestReconciliation().get(0);

        assertThat(result.getReconciliationRate()).isEqualTo(100.0);
        assertThat(result.isReconciled()).isTrue();
        assertThat(result.getStatus()).isEqualTo("Conforme");
    }

    @Test
    void getReconciliationForTableUsesRequestedTableAndDefaultLimit() {
        when(statsRepo.findByTable("CUSTOMER", 30)).thenReturn(List.of(stats("CUSTOMER", 3, 2)));

        ReconciliationDto result = reconciliationService.getReconciliationForTable("CUSTOMER").get(0);

        assertThat(result.getReconciliationRate()).isEqualTo(66.67);
        assertThat(result.getStatus()).isEqualTo("Non conforme");
        verify(statsRepo).findByTable("CUSTOMER", 30);
    }

    private static StatsRecord stats(String table, int oracleCount, int sqlCount) {
        return StatsRecord.builder()
                .jobExecutionId(1L)
                .mssqlTable(table)
                .businessDate(20260728)
                .oracleCount(oracleCount)
                .sqlCount(sqlCount)
                .build();
    }
}
