package wevioo.se.monitoringms.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wevioo.se.monitoringms.config.CacheConfig;
import wevioo.se.monitoringms.model.BatchConfigRecord;
import wevioo.se.monitoringms.repository.BatchConfigRepository;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConfigController.class)
@Import(CacheConfig.class)
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BatchConfigRepository configRepo;

    @Test
    void getAllConfigsListsEveryExtraction() throws Exception {
        when(configRepo.findAll()).thenReturn(List.of(sampleConfig()));

        mockMvc.perform(get("/api/v1/config/batch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("CUSTOMER"))
                .andExpect(jsonPath("$[0].tableName").value("CUSTOMER"))
                .andExpect(jsonPath("$[0].chargement").value("INCREMENTAL"))
                .andExpect(jsonPath("$[0].enabled").value(true));
    }

    @Test
    void getByIdReturnsTheConfiguration() throws Exception {
        when(configRepo.findById("CUSTOMER")).thenReturn(Optional.of(sampleConfig()));

        mockMvc.perform(get("/api/v1/config/batch/CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("CUSTOMER"))
                .andExpect(jsonPath("$.schedulerTime").value("02:00"));
    }

    @Test
    void getByIdReturnsNotFoundForAnUnknownTable() throws Exception {
        when(configRepo.findById("INCONNUE")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/config/batch/INCONNUE"))
                .andExpect(status().isNotFound());
    }

    @Test
    void toggleEnablesAnExtraction() throws Exception {
        when(configRepo.setEnabled("CUSTOMER", true)).thenReturn(1);

        mockMvc.perform(patch("/api/v1/config/batch/CUSTOMER/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("CUSTOMER"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.updated").value(true));

        verify(configRepo).setEnabled("CUSTOMER", true);
    }

    @Test
    void toggleDisablesAnExtraction() throws Exception {
        when(configRepo.setEnabled("CUSTOMER", false)).thenReturn(1);

        mockMvc.perform(patch("/api/v1/config/batch/CUSTOMER/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        verify(configRepo).setEnabled("CUSTOMER", false);
    }

    /** Un corps sans la clé « enabled » désactive l'extraction (Boolean.TRUE.equals(null) = false). */
    @Test
    void toggleTreatsAMissingFlagAsDisabled() throws Exception {
        when(configRepo.setEnabled("CUSTOMER", false)).thenReturn(1);

        mockMvc.perform(patch("/api/v1/config/batch/CUSTOMER/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        verify(configRepo).setEnabled("CUSTOMER", false);
    }

    @Test
    void toggleReturnsNotFoundWhenNoRowWasUpdated() throws Exception {
        when(configRepo.setEnabled("INCONNUE", true)).thenReturn(0);

        mockMvc.perform(patch("/api/v1/config/batch/INCONNUE/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isNotFound());
    }

    private static BatchConfigRecord sampleConfig() {
        return BatchConfigRecord.builder()
                .id("CUSTOMER")
                .tableName("CUSTOMER")
                .t24SelectionName("CUSTOMER")
                .colonneRef("DATE_TIME")
                .chargement("INCREMENTAL")
                .lastDate(20260727L)
                .minDate(20200101L)
                .enabled(true)
                .scheduler(true)
                .schedulerTime("02:00")
                .build();
    }
}
