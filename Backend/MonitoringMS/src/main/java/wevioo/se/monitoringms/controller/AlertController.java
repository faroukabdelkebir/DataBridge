package wevioo.se.monitoringms.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import wevioo.se.monitoringms.dto.AlertDto;
import wevioo.se.monitoringms.service.AlertService;

import java.util.List;
import java.util.Map;

/**
 * Endpoints de gestion des alertes.
 *
 * GET    /api/v1/alerts           → Alertes actives
 * DELETE /api/v1/alerts/{key}     → Résoudre (acquitter) une alerte
 */
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Tag(name = "Alertes", description = "Gestion des alertes actives (échecs, SLA, réconciliation, modifications du schéma)")
public class AlertController {

    /** Moteur d'alertes. */
    private final AlertService alertService;

    /**
     * Retourne les alertes actuellement actives.
     *
     * @return les alertes non acquittées, ou un tableau vide
     */
    @GetMapping
    @Operation(summary = "Liste des alertes actives non résolues")
    public ResponseEntity<List<AlertDto>> getActiveAlerts() {
        return ResponseEntity.ok(alertService.getActiveAlerts());
    }

    /**
     * Acquitte une alerte.
     *
     * <p>L'acquittement n'est pas définitif : si la condition d'origine
     * persiste, la prochaine évaluation recréera l'alerte. Une clé inconnue
     * n'est pas une erreur et donne la même réponse.</p>
     *
     * @param alertKey clé de l'alerte à acquitter
     * @return un accusé reprenant la clé traitée
     */
    @DeleteMapping("/{alertKey}")
    @Operation(summary = "Acquitter (résoudre) une alerte",
            description = "Supprime l'alerte de la liste active. Elle sera recréée si la condition persiste.")
    public ResponseEntity<Map<String, Object>> resolveAlert(@PathVariable String alertKey) {
        alertService.resolveAlert(alertKey);
        return ResponseEntity.ok(Map.of(
                "alertKey", alertKey,
                "resolved", true
        ));
    }
}

