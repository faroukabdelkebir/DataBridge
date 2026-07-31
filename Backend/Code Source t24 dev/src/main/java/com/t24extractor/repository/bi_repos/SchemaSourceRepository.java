package com.t24extractor.repository.bi_repos;

import com.t24extractor.commons.Constants;
import com.t24extractor.model.SchemaSource;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Accès au référentiel des colonnes extraites (SCHEMA_SOURCE).
 */
@Repository
public interface SchemaSourceRepository extends CrudRepository<SchemaSource, String> {

    /**
     * @param tableName table BI
     * @return toutes les colonnes déclarées, actives ou non
     */
    @Query(Constants.SCHEMA_TABLE)
    List<SchemaSource> findAllByTableName(@Param("tableName") String tableName);

    /**
     * @param tableName table BI
     * @param columName colonne recherchée
     * @return la colonne, ou vide si elle n'est pas déclarée
     */
    @Query(Constants.SCHEMA_COLUMN_TABLE)
    Optional<SchemaSource> findAllByTableNameAndColumnName(@Param("tableName") String tableName, @Param("columnName") String columName);


    /**
     * Colonnes actives d'une table.
     *
     * <p>Malgré son nom hérité, cette méthode ne renvoie pas tout : elle filtre
     * sur {@code _enabled = 1}.
     *
     * @param tableName table BI
     * @return les colonnes à extraire
     */
    @Query(Constants.SCHEMA_ENABLED)
    List<SchemaSource> findAll(@Param("tableName") String tableName);

    /**
     * Sert à distinguer une table jamais chargée d'une table sans colonne active.
     *
     * @param tableName table BI
     * @return nombre de colonnes déclarées ; {@code 0} pour une table inconnue
     */
    @Query(Constants.COUNT_ROWS_TABLE_SCHEMA_SOURCE)
    long countByTableName(@Param("tableName") String tableName);

    /**
     * @param tableName table BI
     * @param enabled   conservé pour la signature ; la requête filtre déjà sur
     *                  les colonnes actives
     * @return les colonnes à extraire
     */
    @Query(Constants.SCHEMA_ENABLED)
    List<SchemaSource> findSchemaSourceByTableNameAndEnabled(@Param("tableName") String tableName, @Param("enabled") boolean enabled);

    /**
     * @param tableName table BI
     * @return nombre de colonnes actives
     */
    @Query(Constants.COUNT_ENABLED_ROWS_TABLE_SCHEMA_SOURCE)
    long countAllByTableName(@Param("tableName") String tableName);

    /**
     * Active ou désactive une colonne.
     *
     * @param tableName  table BI
     * @param columnName colonne visée
     * @param enabled    nouvel état
     * @return nombre de lignes modifiées ; {@code 0} si la colonne n'existe pas
     */
    @Modifying
    @Query(Constants.UPDATE_SCHEMA_SOURCE_ENABLED)
    int updateEnabledByTableNameAndColumnName(
            @Param("tableName") String tableName,
            @Param("columnName") String columnName,
            @Param("enabled") boolean enabled
    );

}
