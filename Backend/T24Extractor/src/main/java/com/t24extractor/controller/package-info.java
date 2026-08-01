/**
 * API REST d'administration, exposée sous {@code /api/v1}.
 *
 * <p>Trois familles d'endpoints :
 * <ul>
 *   <li>{@link com.t24extractor.controller.JobLauncherController} — lancement
 *       et arrêt manuels des extractions ;</li>
 *   <li>{@link com.t24extractor.controller.SchedulerConfigController} —
 *       configuration des extractions et de leur planification ;</li>
 *   <li>{@link com.t24extractor.controller.SchemaSourceController} — activation
 *       des colonnes à extraire.</li>
 * </ul>
 *
 * <p>Les contrôleurs sont de simples adaptateurs HTTP : ils délèguent aux
 * services et n'accèdent jamais directement aux dépôts. Tout
 * {@code /api/v1/**} exige l'autorité {@code SCOPE_batch:execute}
 * ({@link com.t24extractor.configuration.SecurityConfig}).
 */
package com.t24extractor.controller;
