package com.t24extractor.repository.bi_repos;


import com.t24extractor.model.MetaDataTable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Registre des champs multivalués éclatés en tables satellites.
 *
 * <p>L'insertion passe par un SQL conditionnel généré dans
 * {@link com.t24extractor.bi_sync.ops.SqlOps} ; ce dépôt ne sert qu'à la
 * suppression lorsqu'un champ multivalué est désactivé.
 */
@Repository
public interface MetaDataRepository extends CrudRepository<MetaDataTable, String> {

}
