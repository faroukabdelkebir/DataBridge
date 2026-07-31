package com.t24extractor.repository.bi_repos;

import com.t24extractor.commons.Constants;
import com.t24extractor.model.SchemaSourceAudit;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Changements de schéma en attente d'application.
 *
 * <p>Les lignes sont consommées puis supprimées par l'étape {@code BI_SYNC} :
 * cette table ne conserve donc que les demandes non encore traitées.
 */
public interface SchemaSourceAuditRepository extends CrudRepository<SchemaSourceAudit, Integer> {

    /**
     * @param tableName table BI
     * @return les changements en attente, vide si le schéma est à jour
     */
    @Query(Constants.SCHEMA_AUDIT_TABLE)
    List<SchemaSourceAudit> findAllBySourceTableName(@Param("tableName") String tableName);

}
