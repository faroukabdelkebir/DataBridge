package com.t24extractor.repository.t24_repo;


import com.t24extractor.model.FileControl;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Classification T24 des fichiers, qui détermine le préfixe de table.
 */
@Repository
public interface FileControlRepository extends CrudRepository<FileControl, String> {

  /**
   * @param recid nom de la table T24, sans suffixe d'historisation
   * @return la classification, ou vide si F_FILE_CONTROL ne la référence pas
   */
  Optional<FileControl> findFileControlByRecid(String recid);
}