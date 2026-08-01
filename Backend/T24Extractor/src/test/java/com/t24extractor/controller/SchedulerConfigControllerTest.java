package com.t24extractor.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.t24extractor.exception.InvalidSchedulerConfigException;
import com.t24extractor.model.EnabledUpdateResult;
import com.t24extractor.model.SchedulerUpdateResult;
import com.t24extractor.model.UpdateEnabledRequest;
import com.t24extractor.model.UpdateSchedulerRequest;
import com.t24extractor.service.ISchedulerConfigService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Le contrôleur n'a plus de logique propre : il traduit la requête HTTP en
 * appel de service et renvoie le résultat. Ces tests vérifient donc uniquement
 * la délégation et l'interprétation du corps de requête — les règles métier
 * sont couvertes par SchedulerConfigServiceImplTest.
 */
class SchedulerConfigControllerTest {

    private final ISchedulerConfigService service = mock(ISchedulerConfigService.class);
    private final SchedulerConfigController controller = new SchedulerConfigController(service);

    @Test
    void returnsConfigurationsFromTheService() {
        List<Map<String, Object>> configs = List.of(Map.of("id", "1"));
        when(service.findAllConfigs()).thenReturn(configs);

        assertSame(configs, controller.getConfigs());
    }

    @Test
    void forwardsSchedulerFlagAndTime() {
        SchedulerUpdateResult expected = new SchedulerUpdateResult("1", true, "06:30", true);
        when(service.updateScheduler("1", true, "06:30")).thenReturn(expected);

        assertSame(expected,
                controller.updateScheduler("1", new UpdateSchedulerRequest(true, "06:30")));
    }

    @Test
    void treatsAMissingSchedulerFlagAsDisabled() {
        controller.updateScheduler("1", new UpdateSchedulerRequest(null, null));

        // Un corps sans "scheduler" vaut désactivation, pas une erreur de binding.
        verify(service).updateScheduler("1", false, null);
        verifyNoMoreInteractions(service);
    }

    @Test
    void passesSchedulerTimeThroughUnchangedForTheServiceToValidate() {
        when(service.updateScheduler("1", true, "  06:30  "))
                .thenThrow(new InvalidSchedulerConfigException("format"));

        // Le contrôleur ne normalise ni ne valide : c'est une règle métier.
        assertThrows(InvalidSchedulerConfigException.class,
                () -> controller.updateScheduler("1", new UpdateSchedulerRequest(true, "  06:30  ")));
    }

    @Test
    void forwardsEnabledToggle() {
        EnabledUpdateResult expected = new EnabledUpdateResult("1", true, true);
        when(service.updateEnabled("1", true)).thenReturn(expected);

        assertSame(expected, controller.toggleEnabled("1", new UpdateEnabledRequest(true)));
    }

    @Test
    void treatsAnEmptyEnabledBodyAsDisabled() {
        controller.toggleEnabled("999", new UpdateEnabledRequest(null));

        verify(service).updateEnabled("999", false);
    }

    @Test
    void requestRecordsExposeNullSafeFlags() {
        assertEquals(false, new UpdateSchedulerRequest(null, null).schedulerEnabled());
        assertEquals(true, new UpdateSchedulerRequest(true, null).schedulerEnabled());
        assertEquals(false, new UpdateEnabledRequest(null).isEnabled());
        assertEquals(true, new UpdateEnabledRequest(true).isEnabled());
    }
}
