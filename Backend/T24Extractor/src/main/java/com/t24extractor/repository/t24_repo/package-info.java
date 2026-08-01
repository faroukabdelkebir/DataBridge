/**
 * Dépôts Spring Data JDBC sur la base <b>T24</b> (Oracle), en lecture seule.
 *
 * <p>Ils exposent les dictionnaires du core banking : correspondance nom T24 →
 * table Oracle, classification des fichiers, STANDARD.SELECTION et état de la
 * clôture journalière.
 *
 * <p>Les lectures de dictionnaire sont mises en cache
 * ({@code @Cacheable}) : elles sont invariantes le temps d'une extraction et
 * seraient sinon rejouées pour chaque table traitée.
 *
 * @see com.t24extractor.configuration.T24DataSourceConfiguration
 * @see com.t24extractor.configuration.CacheConfiguration
 */
package com.t24extractor.repository.t24_repo;
