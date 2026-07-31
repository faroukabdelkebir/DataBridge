package com.t24extractor.model;

/**
 * Dernier jour ouvré connu de T24, au format {@code yyyyMMdd}.
 *
 * <p>Sert de borne haute aux chargements incrémentaux.
 *
 * @param recid          identifiant de la ligne de dates
 * @param laskWorkingDay dernier jour ouvré (l'orthographe du composant reflète
 *                       celle du champ d'origine)
 */
public record T24Dates(String recid , String laskWorkingDay){}
