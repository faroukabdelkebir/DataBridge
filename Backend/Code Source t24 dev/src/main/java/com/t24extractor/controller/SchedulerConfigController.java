package com.t24extractor.controller;

import com.t24extractor.model.EnabledUpdateResult;
import com.t24extractor.model.SchedulerUpdateResult;
import com.t24extractor.model.UpdateEnabledRequest;
import com.t24extractor.model.UpdateSchedulerRequest;
import com.t24extractor.service.ISchedulerConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Consultation et pilotage de la configuration des extractions.
 *
 * <p>Adaptateur HTTP sans logique propre : validation, persistance et décisions
 * fonctionnelles sont portées par
 * {@link com.t24extractor.service.ISchedulerConfigService}, et la traduction
 * des refus en codes de statut par
 * {@link com.t24extractor.exception.ApiExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/scheduler")
public class SchedulerConfigController {

    private final ISchedulerConfigService schedulerConfigService;

    public SchedulerConfigController(ISchedulerConfigService schedulerConfigService) {
        this.schedulerConfigService = schedulerConfigService;
    }

    /**
     * @return la configuration de chaque extraction, clés alignées sur le modèle
     *         attendu par le frontend
     */
    @GetMapping("/config")
    public List<Map<String, Object>> getConfigs() {
        return schedulerConfigService.findAllConfigs();
    }

    /**
     * Planification automatique : bascule on/off et/ou changement d'heure.
     * Corps : { "scheduler": true|false, "schedulerTime": "HH:mm" } —
     * schedulerTime est optionnel (bascule sans changer l'heure existante).
     */
    /**
     * @param id      identifiant de l'extraction dans BATCH_CONFIG
     * @param request nouvel état de planification
     * @return l'état enregistré
     * @throws com.t24extractor.exception.InvalidSchedulerConfigException heure
     *         mal formée, ou activation demandée sans heure connue — rendu en 400
     * @throws com.t24extractor.exception.SchedulerConfigNotFoundException
     *         identifiant inconnu — rendu en 404
     */
    @PatchMapping("/config/{id}")
    public SchedulerUpdateResult updateScheduler(
            @PathVariable String id,
            @RequestBody UpdateSchedulerRequest request) {
        return schedulerConfigService.updateScheduler(
                id, request.schedulerEnabled(), request.schedulerTime());
    }

    /** Active/désactive une extraction (colonne enabled, lue aussi par le scheduler). */
    /**
     * @param id      identifiant de l'extraction dans BATCH_CONFIG
     * @param request nouvel état d'activation ; un corps vide vaut désactivation
     * @return l'état enregistré
     * @throws com.t24extractor.exception.SchedulerConfigNotFoundException
     *         identifiant inconnu — rendu en 404
     */
    @PatchMapping("/config/{id}/enabled")
    public EnabledUpdateResult toggleEnabled(
            @PathVariable String id,
            @RequestBody UpdateEnabledRequest request) {
        return schedulerConfigService.updateEnabled(id, request.isEnabled());
    }
}
