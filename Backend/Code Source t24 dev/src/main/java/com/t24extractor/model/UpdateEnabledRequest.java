package com.t24extractor.model;

/**
 * Corps de PATCH /api/v1/scheduler/config/{id}/enabled.
 * Un corps vide vaut désactivation.
 */
public record UpdateEnabledRequest(Boolean enabled) {

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
