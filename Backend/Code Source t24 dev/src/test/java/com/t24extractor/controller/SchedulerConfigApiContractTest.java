package com.t24extractor.controller;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.t24extractor.exception.ApiExceptionHandler;
import com.t24extractor.exception.InvalidSchedulerConfigException;
import com.t24extractor.exception.SchedulerConfigNotFoundException;
import com.t24extractor.model.EnabledUpdateResult;
import com.t24extractor.model.SchedulerUpdateResult;
import com.t24extractor.service.ISchedulerConfigService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Contrat HTTP de /api/v1/scheduler, vérifié sur le fil.
 *
 * Le passage en couche service a changé les types de retour (ResponseEntity de
 * Map → records) et déporté les erreurs vers ApiExceptionHandler. Le frontend,
 * lui, n'a pas changé : ces tests garantissent que le JSON et les codes de
 * statut produits sont strictement identiques à ceux d'avant le refactoring.
 */
class SchedulerConfigApiContractTest {

    private final ISchedulerConfigService service = mock(ISchedulerConfigService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new SchedulerConfigController(service))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void listsConfigurationsWithTheFrontendKeys() throws Exception {
        when(service.findAllConfigs()).thenReturn(List.of(config()));

        mockMvc.perform(get("/api/v1/scheduler/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].tableName").value("CUSTOMER"))
                .andExpect(jsonPath("$[0].t24SelectionName").value("SS.CUSTOMER"))
                .andExpect(jsonPath("$[0].colonneRef").value("DATE_TIME"))
                .andExpect(jsonPath("$[0].chargement").value("Full"))
                .andExpect(jsonPath("$[0].lastDate").value(20260101L))
                .andExpect(jsonPath("$[0].minDate").value(20250101L))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[0].scheduler").value(false))
                .andExpect(jsonPath("$[0].schedulerTime").value("06:30"));
    }

    @Test
    void updateSchedulerReturnsTheEchoedConfiguration() throws Exception {
        when(service.updateScheduler("1", true, "06:30"))
                .thenReturn(new SchedulerUpdateResult("1", true, "06:30", true));

        mockMvc.perform(patch("/api/v1/scheduler/config/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduler\":true,\"schedulerTime\":\"06:30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.scheduler").value(true))
                .andExpect(jsonPath("$.schedulerTime").value("06:30"))
                .andExpect(jsonPath("$.updated").value(true));
    }

    @Test
    void togglingSchedulerOffReturnsAnEmptyTime() throws Exception {
        when(service.updateScheduler("1", false, null))
                .thenReturn(new SchedulerUpdateResult("1", false, "", true));

        mockMvc.perform(patch("/api/v1/scheduler/config/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduler\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedulerTime").value(""));
    }

    @Test
    void invalidTimeStillReturnsBadRequestWithAnErrorMessage() throws Exception {
        when(service.updateScheduler(anyString(), anyBoolean(), eq("0630")))
                .thenThrow(new InvalidSchedulerConfigException(
                        "schedulerTime doit être au format HH:mm (ex. 06:30)"));

        mockMvc.perform(patch("/api/v1/scheduler/config/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduler\":true,\"schedulerTime\":\"0630\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("schedulerTime doit être au format HH:mm (ex. 06:30)"));
    }

    @Test
    void unknownIdStillReturnsAnEmptyNotFound() throws Exception {
        when(service.updateScheduler(eq("999"), anyBoolean(), anyString()))
                .thenThrow(new SchedulerConfigNotFoundException("999"));

        mockMvc.perform(patch("/api/v1/scheduler/config/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduler\":true,\"schedulerTime\":\"06:30\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    void toggleEnabledReturnsTheEchoedFlag() throws Exception {
        when(service.updateEnabled("1", true)).thenReturn(new EnabledUpdateResult("1", true, true));

        mockMvc.perform(patch("/api/v1/scheduler/config/1/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.updated").value(true));
    }

    @Test
    void anEmptyEnabledBodyIsAcceptedAsDisabling() throws Exception {
        when(service.updateEnabled("1", false)).thenReturn(new EnabledUpdateResult("1", false, true));

        mockMvc.perform(patch("/api/v1/scheduler/config/1/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void unknownBodyPropertiesAreIgnored() throws Exception {
        when(service.updateScheduler("1", true, "06:30"))
                .thenReturn(new SchedulerUpdateResult("1", true, "06:30", true));

        // Le frontend peut envoyer des champs supplémentaires sans casser l'appel.
        mockMvc.perform(patch("/api/v1/scheduler/config/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduler\":true,\"schedulerTime\":\"06:30\",\"tableName\":\"CUSTOMER\"}"))
                .andExpect(status().isOk());
    }

    private Map<String, Object> config() {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", "1");
        dto.put("tableName", "CUSTOMER");
        dto.put("t24SelectionName", "SS.CUSTOMER");
        dto.put("colonneRef", "DATE_TIME");
        dto.put("chargement", "Full");
        dto.put("lastDate", 20260101L);
        dto.put("minDate", 20250101L);
        dto.put("enabled", true);
        dto.put("scheduler", false);
        dto.put("schedulerTime", "06:30");
        return dto;
    }
}
