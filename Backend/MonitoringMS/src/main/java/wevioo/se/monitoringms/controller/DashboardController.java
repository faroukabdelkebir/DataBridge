package wevioo.se.monitoringms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wevioo.se.monitoringms.dto.DashboardSummaryDto;
import wevioo.se.monitoringms.dto.JobExecutionDto;
import wevioo.se.monitoringms.service.DashboardService;

import java.util.List;

/**
 * Endpoints du tableau de bord principal.
 *
 * GET /api/v1/dashboard/summary   → Vue d'ensemble complète (jobs, KPIs, alertes)
 * GET /api/v1/dashboard/jobs/live → Jobs en cours uniquement
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Tableau de bord", description = "Vue d'ensemble du processus d'extraction T24")
public class DashboardController {

    /** Service d'agrégation des données du tableau de bord. */
    private final DashboardService dashboardService;

    /**
     * Retourne la vue d'ensemble du processus d'extraction.
     *
     * @return le résumé complet, toujours avec le statut {@code 200}
     */
    @GetMapping("/summary")
    @Operation(summary = "Résumé complet du tableau de bord",
            description = "Retourne les traitements en cours, les indicateurs sur 7 jours, les alertes actives et le dernier statut COB.")
    public ResponseEntity<DashboardSummaryDto> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }

    /**
     * Retourne les traitements actuellement en cours.
     *
     * @return les exécutions actives, ou un tableau vide si aucune ne tourne
     */
    @GetMapping("/jobs/live")
    @Operation(summary = "Traitements actuellement en cours d'exécution")
    public ResponseEntity<List<JobExecutionDto>> getLiveJobs() {
        return ResponseEntity.ok(dashboardService.getLiveJobs());
    }
}

