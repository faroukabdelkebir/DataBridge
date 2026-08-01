package com.t24extractor.service;

import com.t24extractor.exception.DataNotFoundException;
import com.t24extractor.model.SchemaSource;

import java.util.List;

/**
 * Gestion du schéma des colonnes extraites (table SCHEMA_SOURCE).
 */
public interface ISchemaSourceService {

    /**
     * @param tableName table BI
     * @return les colonnes déclarées pour cette table, éventuellement vide
     */
    List<SchemaSource> findColumnsByTable(String tableName);

    /**
     * Active ou désactive une colonne pour les extractions suivantes.
     *
     * @return la colonne telle qu'enregistrée après mise à jour
     * @throws DataNotFoundException si la colonne n'existe pas
     */
    SchemaSource updateColumnEnabled(String tableName, String columnName, boolean enabled)
            throws DataNotFoundException;
}
