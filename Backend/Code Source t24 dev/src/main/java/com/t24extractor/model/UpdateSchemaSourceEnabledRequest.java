package com.t24extractor.model;

/**
 * Corps de {@code PATCH /api/v1/schema-source/column/enabled}.
 *
 * @param enabled {@code true} pour extraire la colonne, {@code false} pour
 *                l'exclure des prochaines extractions
 */
public record UpdateSchemaSourceEnabledRequest(boolean enabled) {
}
