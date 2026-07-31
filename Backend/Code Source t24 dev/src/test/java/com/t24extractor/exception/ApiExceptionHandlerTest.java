package com.t24extractor.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Le contrat HTTP historique du contrôleur doit être préservé après le passage
 * en couche service : 400 avec un corps {"error": ...}, 404 sans corps.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void invalidConfigurationBecomesBadRequestCarryingTheMessage() {
        ResponseEntity<Map<String, Object>> response = handler.handleInvalidSchedulerConfig(
                new InvalidSchedulerConfigException("schedulerTime doit être au format HH:mm (ex. 06:30)"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("schedulerTime doit être au format HH:mm (ex. 06:30)",
                response.getBody().get("error"));
    }

    @Test
    void unknownConfigurationBecomesAnEmptyNotFound() {
        ResponseEntity<Void> response = handler.handleSchedulerConfigNotFound(
                new SchedulerConfigNotFoundException("999"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void notFoundExceptionNamesTheMissingConfiguration() {
        assertEquals("Configuration batch introuvable : 999",
                new SchedulerConfigNotFoundException("999").getMessage());
    }
}
