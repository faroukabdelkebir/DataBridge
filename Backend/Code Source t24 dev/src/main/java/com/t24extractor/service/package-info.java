/**
 * Couche service : règles métier de l'API d'administration.
 *
 * <p>Les services portent la validation, l'accès aux données et les décisions
 * fonctionnelles ; ils ignorent HTTP et signalent les refus par des exceptions
 * métier que {@link com.t24extractor.exception.ApiExceptionHandler} traduit en
 * codes de statut. Les contrôleurs ne font que router.
 *
 * <p>Convention du projet : une interface {@code I…Service} et une
 * implémentation {@code …ServiceImpl} annotée {@code @Service}.
 */
package com.t24extractor.service;
