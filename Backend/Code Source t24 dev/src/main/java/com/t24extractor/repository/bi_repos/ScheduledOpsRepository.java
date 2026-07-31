package com.t24extractor.repository.bi_repos;


import com.t24extractor.model.ScheduledOps;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.t24extractor.commons.Constants.SCHEDULED_OPS_QUERY;

/**
 * File des opérations de maintenance différées (création d'index notamment).
 */
@Repository
public interface ScheduledOpsRepository extends CrudRepository<ScheduledOps, Long> {

    /**
     * @param tableName table BI, telle qu'enregistrée (nom échappé)
     * @return les opérations en attente pour cette table
     */
    @Query(SCHEDULED_OPS_QUERY)
    List<ScheduledOps> findAllByTable(@Param("table") String tableName);

}
