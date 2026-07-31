package wevioo.se.monitoringms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import wevioo.se.monitoringms.model.SchemaAuditRecord;
import wevioo.se.monitoringms.model.SchemaSourceRecord;
import wevioo.se.monitoringms.repository.SchemaRepository;
import wevioo.se.monitoringms.service.SchemaService;

import java.util.List;

/**
 * Endpoints du schéma T24.
 *
 * GET /api/v1/schema/summary           → Résumé par table (total / actives / indexées)
 * GET /api/v1/schema/columns           → Toutes les colonnes
 * GET /api/v1/schema/columns/{table}   → Colonnes d'une table
 * GET /api/v1/schema/audit             → Dernières modifications (trigger)
 * GET /api/v1/schema/audit/{table}     → Audit pour une table
 *
 * <p><strong>Noms de tables T24 et encodage d'URL.</strong> Les tables
 * historisées portent un « # » ({@code FUNDS_TRANSFER#HIS}), caractère qui
 * délimite le fragment d'une URL. Les clients doivent l'encoder en
 * {@code %23} : à défaut, seule la partie gauche du nom parvient au
 * contrôleur et la réponse porte sur une autre table.</p>
 */
@RestController
@RequestMapping("/api/v1/schema")
@RequiredArgsConstructor
@Tag(name = "Schéma", description = "Exploration du dictionnaire SCHEMA_SOURCE et historique des évolutions")
public class SchemaController {

    /** Service d'exploration du dictionnaire de schéma. */
    private final SchemaService schemaService;

    /**
     * Résume la couverture du dictionnaire, table par table.
     *
     * @return un résumé par table T24, trié par nom de table
     */
    @GetMapping("/summary")
    @Operation(summary = "Résumé du schéma par table T24")
    public ResponseEntity<List<SchemaRepository.TableSchemaSummary>> getSummary() {
        return ResponseEntity.ok(schemaService.getSchemaSummary());
    }

    /**
     * Retourne l'intégralité du dictionnaire de schéma.
     *
     * @return toutes les colonnes connues, triées par table puis par colonne
     */
    @GetMapping("/columns")
    @Operation(summary = "Toutes les colonnes du schéma")
    public ResponseEntity<List<SchemaSourceRecord>> getAllColumns() {
        return ResponseEntity.ok(schemaService.getAllColumns());
    }

    /**
     * Retourne les colonnes déclarées pour une table T24.
     *
     * @param table nom de la table T24 ; le « # » des tables historisées doit
     *              être encodé en {@code %23}
     * @return les colonnes de la table, ou un tableau vide si elle est absente
     *         du dictionnaire
     */
    @GetMapping("/columns/{table}")
    @Operation(summary = "Colonnes d'une table T24 spécifique")
    public ResponseEntity<List<SchemaSourceRecord>> getColumnsByTable(@PathVariable String table) {
        return ResponseEntity.ok(schemaService.getColumnsByTable(table));
    }

    /**
     * Retourne les dernières modifications de schéma, toutes tables confondues.
     *
     * @param limit nombre maximal d'événements ; vaut 50 par défaut
     * @return les événements du plus récent au plus ancien
     */
    @GetMapping("/audit")
    @Operation(summary = "Dernières modifications du schéma (déclencheur SCHEMA_SOURCE_AUDIT)")
    public ResponseEntity<List<SchemaAuditRecord>> getRecentAudit(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(schemaService.getRecentAuditEvents(limit));
    }

    /**
     * Retourne l'historique des modifications d'une table.
     *
     * @param table nom de la table T24 ; le « # » doit être encodé en {@code %23}
     * @return les événements du plus récent au plus ancien, ou un tableau vide
     *         si la table n'a jamais été modifiée
     */
    @GetMapping("/audit/{table}")
    @Operation(summary = "Historique des modifications pour une table donnée")
    public ResponseEntity<List<SchemaAuditRecord>> getAuditByTable(@PathVariable String table) {
        return ResponseEntity.ok(schemaService.getAuditByTable(table));
    }
}

