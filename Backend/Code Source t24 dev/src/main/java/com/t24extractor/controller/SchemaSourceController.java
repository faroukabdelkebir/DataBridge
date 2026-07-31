package com.t24extractor.controller;

import com.t24extractor.exception.DataNotFoundException;
import com.t24extractor.model.SchemaSource;
import com.t24extractor.model.UpdateSchemaSourceEnabledRequest;
import com.t24extractor.service.ISchemaSourceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consultation et activation des colonnes extraites.
 *
 * <p>Adaptateur HTTP sans logique propre : la mise à jour est portée par
 * {@link com.t24extractor.service.ISchemaSourceService}.
 *
 * <p>Une modification ne prend effet qu'au job suivant : elle est enregistrée
 * dans SCHEMA_SOURCE puis appliquée à la table BI par l'étape BI_SYNC.
 */
@RestController
@RequestMapping("/api/v1/schema-source")
public class SchemaSourceController {

    private final ISchemaSourceService schemaSourceService;

    public SchemaSourceController(ISchemaSourceService schemaSourceService) {
        this.schemaSourceService = schemaSourceService;
    }

    /**
     * @param tableName table BI
     * @return les colonnes déclarées, actives ou non ; liste vide si la table
     *         est inconnue
     */
    @GetMapping("/{tableName}")
    public List<SchemaSource> getColumnsByTable(@PathVariable String tableName) {
        return schemaSourceService.findColumnsByTable(tableName);
    }

    /**
     * @param tableName  table BI
     * @param columnName colonne à activer ou désactiver
     * @param request    nouvel état
     * @return la colonne telle qu'enregistrée
     * @throws DataNotFoundException si la colonne n'existe pas — rendu en 404
     */
    @PatchMapping("/column/enabled")
    public SchemaSource updateColumnEnabled(
            @RequestParam String tableName,
            @RequestParam String columnName,
            @RequestBody UpdateSchemaSourceEnabledRequest request
    ) throws DataNotFoundException {
        return schemaSourceService.updateColumnEnabled(tableName, columnName, request.enabled());
    }
}
