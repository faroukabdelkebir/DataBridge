package com.t24extractor.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Traduit les exceptions métier en réponses HTTP.
 *
 * C'est ici — et non dans les contrôleurs — que vit la correspondance
 * « erreur métier → code HTTP », afin que les services restent indépendants
 * de la couche web.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidSchedulerConfigException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidSchedulerConfig(
            InvalidSchedulerConfigException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(SchedulerConfigNotFoundException.class)
    public ResponseEntity<Void> handleSchedulerConfigNotFound(SchedulerConfigNotFoundException exception) {
        return ResponseEntity.notFound().build();
    }
}
