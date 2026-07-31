package wevioo.se.monitoringms.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wevioo.se.monitoringms.config.CacheConfig;
import wevioo.se.monitoringms.dto.AlertDto;
import wevioo.se.monitoringms.service.AlertService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertController.class)
@Import(CacheConfig.class)
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlertService alertService;

    @Test
    void getActiveAlertsExposesTheI18nKeysAndParameters() throws Exception {
        when(alertService.getActiveAlerts()).thenReturn(List.of(AlertDto.builder()
                .alertKey("CONSECUTIVE_FAILURE_T24")
                .typeKey("alerts.type.consecutiveFailure")
                .severity("CRITICAL")
                .jobName("T24")
                .messageKey("alerts.msg.consecutiveFailure")
                .params(Map.of("count", 3))
                .triggeredAt(LocalDateTime.of(2026, 7, 28, 8, 0))
                .resolved(false)
                .build()));

        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].alertKey").value("CONSECUTIVE_FAILURE_T24"))
                .andExpect(jsonPath("$[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$[0].jobName").value("T24"))
                .andExpect(jsonPath("$[0].messageKey").value("alerts.msg.consecutiveFailure"))
                .andExpect(jsonPath("$[0].params.count").value(3))
                .andExpect(jsonPath("$[0].resolved").value(false));
    }

    @Test
    void getActiveAlertsReturnsAnEmptyArrayWhenNothingIsWrong() throws Exception {
        when(alertService.getActiveAlerts()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void resolveAlertAcknowledgesTheAlertAndEchoesItsKey() throws Exception {
        mockMvc.perform(delete("/api/v1/alerts/SLA_BREACH_T24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertKey").value("SLA_BREACH_T24"))
                .andExpect(jsonPath("$.resolved").value(true));

        verify(alertService).resolveAlert("SLA_BREACH_T24");
    }
}
