package wevioo.se.monitoringms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import wevioo.se.monitoringms.model.BatchConfigRecord;
import wevioo.se.monitoringms.repository.BatchConfigRepository;

import java.util.List;
import java.util.Map;

/**
 * Endpoints de configuration des extractions.
 *
 * GET   /api/v1/config/batch          → Liste toutes les configs BATCH_CONFIG
 * GET   /api/v1/config/batch/{id}     → Config d'une table
 * PATCH /api/v1/config/batch/{id}     → Activer/désactiver une extraction
 */
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@Tag(name = "Configuration", description = "Consultation et mise à jour de la configuration BATCH_CONFIG")
public class ConfigController {

    /**
     * Accès direct au paramétrage des extractions. Ce contrôleur ne passe pas
     * par un service : les opérations exposées se réduisent à de la lecture et
     * à une bascule d'indicateur, sans règle métier à porter.
     */
    private final BatchConfigRepository configRepo;

    /**
     * Retourne l'ensemble des configurations d'extraction.
     *
     * @return toutes les configurations, triées par identifiant
     */
    @GetMapping("/batch")
    @Operation(summary = "Liste toutes les configurations d'extraction")
    public ResponseEntity<List<BatchConfigRecord>> getAllConfigs() {
        return ResponseEntity.ok(configRepo.findAll());
    }

    /**
     * Retourne la configuration d'une table.
     *
     * @param id identifiant de la configuration, généralement le nom de la table
     * @return la configuration demandée, ou le statut {@code 404} si
     *         l'identifiant est inconnu
     */
    @GetMapping("/batch/{id}")
    @Operation(summary = "Configuration d'une table spécifique")
    public ResponseEntity<BatchConfigRecord> getById(@PathVariable String id) {
        return configRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Active ou désactive une extraction.
     *
     * <p>Seule opération d'écriture exposée par l'API. Une clé {@code enabled}
     * absente ou nulle vaut désactivation : l'indicateur n'est jamais laissé
     * dans un état indéterminé.</p>
     *
     * @param id   identifiant de la configuration
     * @param body corps de requête de la forme <code>{ "enabled": true }</code>
     * @return un accusé reprenant l'identifiant et l'état appliqué, ou le
     *         statut {@code 404} si aucune ligne n'a été mise à jour
     */
    @PatchMapping("/batch/{id}/toggle")
    @Operation(summary = "Activer ou désactiver une extraction",
            description = "Corps de la requête : { \"enabled\": true|false }")
    public ResponseEntity<Map<String, Object>> toggleEnabled(
            @PathVariable String id,
            @RequestBody Map<String, Boolean> body) {

        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        int updated = configRepo.setEnabled(id, enabled);

        if (updated == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "id", id,
                "enabled", enabled,
                "updated", true
        ));
    }
}
