/**
 * Exceptions métier et traduction en réponses HTTP.
 *
 * <p>Les services lèvent des exceptions décrivant un refus fonctionnel, sans
 * référence à HTTP. {@link com.t24extractor.exception.ApiExceptionHandler} les
 * convertit en codes de statut, ce qui garde la couche service indépendante du
 * web et centralise le format des erreurs renvoyées au frontend.
 */
package com.t24extractor.exception;
