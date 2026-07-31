package com.t24extractor.exception;

/**
 * Configuration de planification refusée par les règles métier
 * (format d'heure invalide, activation sans heure connue).
 */
public class InvalidSchedulerConfigException extends RuntimeException {

    public InvalidSchedulerConfigException(String message) {
        super(message);
    }
}
