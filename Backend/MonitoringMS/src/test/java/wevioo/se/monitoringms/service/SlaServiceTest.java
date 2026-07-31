package wevioo.se.monitoringms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import wevioo.se.monitoringms.dto.SlaStatusDto;
import wevioo.se.monitoringms.model.BatchJobExecution;
import wevioo.se.monitoringms.repository.BatchExecutionRepository;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaServiceTest {

    @Mock
    private BatchExecutionRepository batchRepo;

    private SlaService slaService;

    @BeforeEach
    void setUp() {
        slaService = new SlaService(batchRepo);
        ReflectionTestUtils.setField(slaService, "slaCustomer", "07:00");
        ReflectionTestUtils.setField(slaService, "slaFtHis", "07:30");
        ReflectionTestUtils.setField(slaService, "slaAaBill", "07:30");
        ReflectionTestUtils.setField(slaService, "slaMain", "08:00");
    }

    @Test
    void getSlaStatusReturnsOnTimeWhenCompletionIsBeforeDeadline() {
        when(batchRepo.findLastSuccessfulByJobName("T24")).thenReturn(Optional.of(jobFinishedAt(7, 55)));

        SlaStatusDto result = slaService.getSlaStatus("T24");

        assertThat(result.isSlaBreached()).isFalse();
        assertThat(result.getLateBySeconds()).isZero();
        assertThat(result.getStatus()).isEqualTo("Dans les délais");
        assertThat(result.getSlaDeadline()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    void getSlaStatusReturnsBreachWhenCompletionIsAfterDeadline() {
        when(batchRepo.findLastSuccessfulByJobName("CUSTOMER.JOB")).thenReturn(Optional.of(jobFinishedAt(7, 15)));

        SlaStatusDto result = slaService.getSlaStatus("CUSTOMER.JOB");

        assertThat(result.isSlaBreached()).isTrue();
        assertThat(result.getLateBySeconds()).isEqualTo(900);
        assertThat(result.getStatus()).isEqualTo("Délai dépassé");
    }

    @Test
    void getSlaStatusReturnsNotExecutedWhenNoCompletedOrRunningJobExists() {
        when(batchRepo.findLastSuccessfulByJobName("FT.HIS.JOB")).thenReturn(Optional.empty());
        when(batchRepo.findRunning()).thenReturn(List.of());

        SlaStatusDto result = slaService.getSlaStatus("FT.HIS.JOB");

        assertThat(result.isSlaBreached()).isFalse();
        assertThat(result.getLateBySeconds()).isZero();
        assertThat(result.getStatus()).isEqualTo("Non exécuté");
    }

    @Test
    void getAllSlaStatusesReturnsConfiguredJobsInOrder() {
        when(batchRepo.findLastSuccessfulByJobName("T24")).thenReturn(Optional.of(jobFinishedAt(7, 55)));
        when(batchRepo.findLastSuccessfulByJobName("CUSTOMER.JOB")).thenReturn(Optional.of(jobFinishedAt(6, 55)));
        when(batchRepo.findLastSuccessfulByJobName("AA.BILL.DETAILS.JOB")).thenReturn(Optional.of(jobFinishedAt(7, 20)));
        when(batchRepo.findLastSuccessfulByJobName("FT.HIS.JOB")).thenReturn(Optional.of(jobFinishedAt(7, 25)));

        List<SlaStatusDto> result = slaService.getAllSlaStatuses();

        assertThat(result).extracting(SlaStatusDto::getJobName)
                .containsExactly("T24", "CUSTOMER.JOB", "AA.BILL.DETAILS.JOB", "FT.HIS.JOB");
    }

    private static BatchJobExecution jobFinishedAt(int hour, int minute) {
        LocalDateTime endTime = LocalDateTime.of(2026, 7, 28, hour, minute);
        return BatchJobExecution.builder()
                .jobExecutionId(1L)
                .jobName("T24")
                .status("COMPLETED")
                .startTime(endTime.minusMinutes(10))
                .endTime(endTime)
                .build();
    }
}
