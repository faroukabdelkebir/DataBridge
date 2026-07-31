package com.t24extractor.repository.t24_repo;

import com.t24extractor.model.CobStatus;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * État de la clôture journalière T24.
 */
@Repository
public interface CobRepository extends CrudRepository<CobStatus, String> {

  /**
   * @param recid identifiant de la ligne de service, en pratique {@code COB}
   * @return l'état du service, ou vide si la table T24 n'est pas alimentée
   */
  Optional<CobStatus> findByRecid(String recid);
}
