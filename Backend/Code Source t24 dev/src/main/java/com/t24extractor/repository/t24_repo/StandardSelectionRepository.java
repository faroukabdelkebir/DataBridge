package com.t24extractor.repository.t24_repo;

import com.t24extractor.model.StandardSelectionRecord;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Dictionnaires STANDARD.SELECTION des tables T24.
 */
@Repository
public interface StandardSelectionRepository extends CrudRepository<StandardSelectionRecord, String> {

    /**
     * Le dictionnaire est invariant pendant une extraction : il est mis en cache
     * pour éviter de relire le même XML à chaque table traitée.
     *
     * @param t24TableName nom T24 sans suffixe d'historisation ({@code CUSTOMER},
     *                     et non {@code CUSTOMER$HIS})
     * @return le dictionnaire, ou vide s'il n'est pas défini
     */
    @Override
    @Cacheable(value = "ss")
    Optional<StandardSelectionRecord> findById(String t24TableName);
}
