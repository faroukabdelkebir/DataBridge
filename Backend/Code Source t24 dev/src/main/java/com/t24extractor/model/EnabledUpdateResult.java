package com.t24extractor.model;

/** Résultat d'une activation/désactivation d'extraction. */
public record EnabledUpdateResult(String id, boolean enabled, boolean updated) {
}
