package wevioo.se.monitoringms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import wevioo.se.monitoringms.dto.AlertDto;
import wevioo.se.monitoringms.dto.JobExecutionDto;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SsePublisherTest {

    @Mock
    private DashboardService dashboardService;

    @Mock
    private AlertService alertService;

    private SsePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new SsePublisher(new ObjectMapper(), dashboardService, alertService);
    }

    @Test
    void subscribeRegistersAClientWithAFiveMinuteTimeout() {
        SseEmitter emitter = publisher.subscribe();

        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(5 * 60 * 1000L);
        assertThat(clients()).hasSize(1);
    }

    @Test
    void subscribeGivesEachClientADistinctIdentifier() {
        publisher.subscribe();
        publisher.subscribe();

        assertThat(clients()).hasSize(2);
        assertThat(clients().keySet()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void broadcastsAreSkippedEntirelyWhenNoClientIsConnected() {
        publisher.broadcastLiveJobs();
        publisher.broadcastAlerts();
        publisher.heartbeat();

        verifyNoInteractions(dashboardService, alertService);
    }

    @Test
    void broadcastLiveJobsSendsTheJobsEventToConnectedClients() throws IOException {
        SseEmitter emitter = registerMockClient();
        when(dashboardService.getLiveJobs()).thenReturn(List.of(
                JobExecutionDto.builder().jobExecutionId(1L).jobName("T24").running(true).build()));

        publisher.broadcastLiveJobs();

        verify(dashboardService).getLiveJobs();
        verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(clients()).hasSize(1);
    }

    @Test
    void broadcastAlertsSendsTheAlertsEventToConnectedClients() throws IOException {
        SseEmitter emitter = registerMockClient();
        when(alertService.getActiveAlerts()).thenReturn(List.of(
                AlertDto.builder().alertKey("SLA_BREACH_T24").severity("CRITICAL").build()));

        publisher.broadcastAlerts();

        verify(alertService).getActiveAlerts();
        verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void heartbeatSendsAKeepaliveToConnectedClients() throws IOException {
        SseEmitter emitter = registerMockClient();

        publisher.heartbeat();

        verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void disconnectedClientsAreDroppedOnTheNextBroadcast() throws IOException {
        SseEmitter dead = registerMockClient();
        doThrow(new IOException("client parti")).when(dead).send(any(SseEmitter.SseEventBuilder.class));

        publisher.heartbeat();

        assertThat(clients()).isEmpty();
    }

    @Test
    void aBroadcastFailureNeverPropagatesToTheScheduler() {
        registerMockClient();
        when(dashboardService.getLiveJobs()).thenThrow(new IllegalStateException("base indisponible"));
        when(alertService.getActiveAlerts()).thenThrow(new IllegalStateException("base indisponible"));

        assertThatCode(() -> publisher.broadcastLiveJobs()).doesNotThrowAnyException();
        assertThatCode(() -> publisher.broadcastAlerts()).doesNotThrowAnyException();
    }

    @Test
    void publishJobEventForwardsArbitraryPayloadsToClients() throws IOException {
        SseEmitter emitter = registerMockClient();

        publisher.publishJobEvent("job-finished", Map.of("jobName", "T24"));

        verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void anUnserializablePayloadIsDroppedWithoutSendingAnything() throws IOException {
        SseEmitter emitter = registerMockClient();

        publisher.publishJobEvent("broken", new Object());

        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(clients()).hasSize(1);
    }

    /** Enregistre un émetteur mocké directement dans le registre interne. */
    private SseEmitter registerMockClient() {
        SseEmitter emitter = org.mockito.Mockito.mock(SseEmitter.class);
        clients().put(1L, emitter);
        return emitter;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, SseEmitter> clients() {
        return (Map<Long, SseEmitter>) ReflectionTestUtils.getField(publisher, "clients");
    }
}