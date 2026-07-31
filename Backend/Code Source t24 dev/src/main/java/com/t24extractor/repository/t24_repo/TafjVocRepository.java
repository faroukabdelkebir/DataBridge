package com.t24extractor.repository.t24_repo;

import com.t24extractor.model.TafjVoc;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.repository.CrudRepository;

/**
 * Dictionnaire TAFJ : correspondance nom T24 ↔ table Oracle.
 */
public interface TafjVocRepository extends CrudRepository<TafjVoc, String> {

    /**
     * @param recid nom T24 qualifié, par exemple {@code FBNK.CUSTOMER}
     * @return la correspondance, ou vide si la table est inconnue de TAFJ
     */
    Optional<TafjVoc> findTafjVocByRecid(String recid);

    /**
     * Recherche inverse, à partir du nom de la table Oracle.
     *
     * @param orclFileName table Oracle, par exemple {@code FBNK_CUSTOMER}
     * @return la correspondance, ou vide si elle n'existe pas
     */
    @Cacheable(value = "tafj")
    Optional<TafjVoc> findTafjVocByOrclFileName(String orclFileName);
}
