/**
 * Câblage Spring de l'application.
 *
 * <ul>
 *   <li>{@link com.t24extractor.configuration.BatchConfiguration} — tasklets,
 *       steps, flows et jobs. Les noms qui y sont déclarés ({@code T24},
 *       {@code CUSTOMER.JOB}…) sont référencés ailleurs dans le code : les
 *       changer sans précaution casse le lancement et l'arrêt des jobs ;</li>
 *   <li>{@link com.t24extractor.configuration.T24DataSourceConfiguration} et
 *       {@link com.t24extractor.configuration.BIDataSourceConfiguration} — deux
 *       piles Spring Data JDBC indépendantes (dialecte, converter, transactions)
 *       pour Oracle et SQL Server ;</li>
 *   <li>{@link com.t24extractor.configuration.BatchInfrastructureConfiguration} —
 *       place les métadonnées Spring Batch côté BI, la source Oracle étant en
 *       lecture seule ;</li>
 *   <li>{@link com.t24extractor.configuration.SecurityConfig} — validation des
 *       jetons OAuth2 et contrôle d'accès aux endpoints ;</li>
 *   <li>{@link com.t24extractor.configuration.CacheConfiguration} — cache
 *       Caffeine des dictionnaires T24.</li>
 * </ul>
 */
package com.t24extractor.configuration;
