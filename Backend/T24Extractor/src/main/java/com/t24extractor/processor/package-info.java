/**
 * Transformation des enregistrements T24 en documents JSON.
 *
 * <p>{@link com.t24extractor.processor.XMLProcessor} est le cœur du package :
 * il décode le XMLRECORD de T24 en s'appuyant sur le dictionnaire produit à
 * l'étape STANDARD.SELECTION, et restitue la multivaluation T24 (attributs
 * {@code m} et {@code s}) sous forme de collections indexées.
 *
 * <p>Les deux {@code …FilterProcessor} écartent les enregistrements déjà
 * présents en base, pour les reprises après incident.
 */
package com.t24extractor.processor;
