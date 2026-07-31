package com.t24extractor.model;

/**
 * Résultat d'une mise à jour de planification, renvoyé tel quel au frontend.
 * {@code schedulerTime} vaut "" lorsque l'heure enregistrée n'a pas été modifiée.
 */
public record SchedulerUpdateResult(String id, boolean scheduler, String schedulerTime, boolean updated) {
}
