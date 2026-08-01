package com.t24extractor.repository.bi_repos;


import com.t24extractor.model.Stats;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Volumétries d'exécution, pour le rapprochement source/cible.
 */
@Repository
public interface StatsRepository extends CrudRepository<Stats, Long> {
}
