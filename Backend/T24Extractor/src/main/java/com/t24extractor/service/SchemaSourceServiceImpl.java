package com.t24extractor.service;

import com.t24extractor.exception.DataNotFoundException;
import com.t24extractor.model.SchemaSource;
import com.t24extractor.repository.bi_repos.SchemaSourceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implémentation adossée à SCHEMA_SOURCE.
 *
 * <p>Après une mise à jour, la colonne est relue afin que la réponse reflète
 * l'état réellement persisté plutôt que celui demandé.
 */
@Service
public class SchemaSourceServiceImpl implements ISchemaSourceService {

    private final SchemaSourceRepository schemaSourceRepository;

    public SchemaSourceServiceImpl(SchemaSourceRepository schemaSourceRepository) {
        this.schemaSourceRepository = schemaSourceRepository;
    }

    @Override
    public List<SchemaSource> findColumnsByTable(String tableName) {
        return schemaSourceRepository.findAllByTableName(tableName);
    }

    @Override
    public SchemaSource updateColumnEnabled(String tableName, String columnName, boolean enabled)
            throws DataNotFoundException {

        int updatedRows =
                schemaSourceRepository.updateEnabledByTableNameAndColumnName(tableName, columnName, enabled);
        if (updatedRows == 0) {
            throw new DataNotFoundException(notFoundMessage(tableName, columnName));
        }

        // Relecture : la colonne renvoyée reflète l'état réellement persisté.
        return schemaSourceRepository.findAllByTableNameAndColumnName(tableName, columnName)
                .orElseThrow(() -> new DataNotFoundException(notFoundMessage(tableName, columnName)));
    }

    private String notFoundMessage(String tableName, String columnName) {
        return "Schema source column not found: " + tableName + "." + columnName;
    }
}
