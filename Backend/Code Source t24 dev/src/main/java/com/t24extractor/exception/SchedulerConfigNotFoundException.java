package com.t24extractor.exception;

/** Aucune ligne BATCH_CONFIG ne porte l'identifiant demandé. */
public class SchedulerConfigNotFoundException extends RuntimeException {

    public SchedulerConfigNotFoundException(String id) {
        super("Configuration batch introuvable : " + id);
    }
}
