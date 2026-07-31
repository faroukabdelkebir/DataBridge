package com.t24extractor.model;

/**
 * Corps de PATCH /api/v1/scheduler/config/{id}.
 *
 * @param scheduler     activation de la planification automatique
 * @param schedulerTime heure "HH:mm", optionnelle (bascule sans changer l'heure existante)
 */
public record UpdateSchedulerRequest(Boolean scheduler, String schedulerTime) {

    public boolean schedulerEnabled() {
        return Boolean.TRUE.equals(scheduler);
    }
}
