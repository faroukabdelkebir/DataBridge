package wevioo.se.monitoringms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import wevioo.se.monitoringms.dto.KpiDto;
import wevioo.se.monitoringms.dto.ReconciliationDto;
import wevioo.se.monitoringms.dto.SlaStatusDto;
import wevioo.se.monitoringms.service.KpiService;
import wevioo.se.monitoringms.service.ReconciliationService;
import wevioo.se.monitoringms.service.SlaService;

import java.util.List;

/**
 * Endpoints KPI, réconciliation et SLA.
 *
 * GET /api/v1/kpis                         → KPIs agrégés (fenêtre configurable)
 * GET /api/v1/kpis/reconciliation          → Réconciliation Oracle / SQL Server par table
 * GET /api/v1/kpis/reconciliation/{table}  → Historique pour une table
 * GET /api/v1/kpis/sla                     → Statut SLA de tous les jobs
 * GET /api/v1/kpis/sla/{jobName}           → SLA d'un job spécifique
 */
@RestController
@RequestMapping("/api/v1/kpis")
@RequiredArgsConstructor
@Tag(name = "Indicateurs", description = "Indicateurs de performance, réconciliation et SLA")
public class KpiController {

    /** Service de calcul des indicateurs de performance. */
    private final KpiService kpiService;

    /** Service de réconciliation Oracle ↔ SQL Server. */
    private final ReconciliationService reconciliationService;

    /** Service de suivi des fenêtres SLA. */
    private final SlaService slaService;

    // ── KPIs globaux ─────────────────────────────────────────────────────────

    /**
     * Retourne les indicateurs agrégés sur une fenêtre glissante.
     *
     * <p>La fenêtre demandée est ramenée dans l'intervalle de 1 à 90 jours :
     * une valeur hors bornes est corrigée plutôt que rejetée.</p>
     *
     * @param days profondeur de la fenêtre en jours ; vaut 7 par défaut
     * @return les indicateurs calculés
     */
    @GetMapping
    @Operation(summary = "Indicateurs agrégés",
            description = "Taux de succès, débit, volumes, durées moyennes et tendance journalière.")
    public ResponseEntity<KpiDto> getKpis(
            @Parameter(description = "Fenêtre d'analyse en jours (défaut : 7, maximum : 90)")
            @RequestParam(defaultValue = "7") int days) {

        int windowDays = Math.clamp(days, 1, 90);
        return ResponseEntity.ok(kpiService.computeKpis(windowDays));
    }

    // ── Réconciliation ────────────────────────────────────────────────────────

    /**
     * Retourne le dernier état de réconciliation de chaque table extraite.
     *
     * @return un état par table, qualifié selon la tolérance configurée
     */
    @GetMapping("/reconciliation")
    @Operation(summary = "Réconciliation Oracle ↔ SQL Server (dernières exécutions)",
            description = "Compare oracle_count vs mongo_count de la table stats pour chaque table T24.")
    public ResponseEntity<List<ReconciliationDto>> getReconciliation() {
        return ResponseEntity.ok(reconciliationService.getLatestReconciliation());
    }

    /**
     * Retourne l'historique de réconciliation d'une table.
     *
     * @param table nom de la table SQL Server
     * @param limit paramètre accepté pour compatibilité mais non transmis au
     *              service, qui applique sa propre borne de 30 résultats
     * @return les états du plus récent au plus ancien, ou un tableau vide si
     *         la table est inconnue
     */
    @GetMapping("/reconciliation/{table}")
    @Operation(summary = "Historique de réconciliation pour une table donnée")
    public ResponseEntity<List<ReconciliationDto>> getReconciliationByTable(
            @PathVariable String table,
            @RequestParam(defaultValue = "30") int limit) {

        return ResponseEntity.ok(
                reconciliationService.getReconciliationForTable(table));
    }

    // ── SLA ───────────────────────────────────────────────────────────────────

    /**
     * Retourne l'état de respect des fenêtres SLA de tous les traitements
     * surveillés.
     *
     * @return un état par traitement, dans l'ordre de déclaration
     */
    @GetMapping("/sla")
    @Operation(summary = "Statut SLA de tous les traitements",
            description = "Indique si chaque traitement a respecté sa fenêtre SLA définie dans la configuration.")
    public ResponseEntity<List<SlaStatusDto>> getAllSla() {
        return ResponseEntity.ok(slaService.getAllSlaStatuses());
    }

    /**
     * Retourne l'état de respect de la fenêtre SLA d'un traitement.
     *
     * @param jobName nom logique du traitement
     * @return l'état correspondant ; un traitement non configuré se voit
     *         appliquer l'heure limite par défaut de 8 h 00
     */
    @GetMapping("/sla/{jobName}")
    @Operation(summary = "Statut SLA d'un traitement spécifique")
    public ResponseEntity<SlaStatusDto> getSlaByJob(@PathVariable String jobName) {
        return ResponseEntity.ok(slaService.getSlaStatus(jobName));
    }
}
