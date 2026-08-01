package com.t24extractor.service;

import com.t24extractor.model.EnabledUpdateResult;
import com.t24extractor.model.SchedulerUpdateResult;

import java.util.List;
import java.util.Map;

/**
 * Pilotage des extractions planifiées (table BATCH_CONFIG), lues par
 * l'AutoExtractionScheduler.
 */
public interface ISchedulerConfigService {

    /** @return la configuration de chaque extraction, dans le vocabulaire du frontend */
    List<Map<String, Object>> findAllConfigs();

    /**
     * Active/désactive la planification et, optionnellement, change son heure.
     *
     * @param schedulerTime heure "HH:mm", ou {@code null} pour conserver l'existante
     * @throws com.t24extractor.exception.InvalidSchedulerConfigException heure invalide,
     *         ou activation demandée alors qu'aucune heure n'est connue
     * @throws com.t24extractor.exception.SchedulerConfigNotFoundException identifiant inconnu
     */
    SchedulerUpdateResult updateScheduler(String id, boolean scheduler, String schedulerTime);

    /**
     * Active/désactive une extraction.
     *
     * @throws com.t24extractor.exception.SchedulerConfigNotFoundException identifiant inconnu
     */
    EnabledUpdateResult updateEnabled(String id, boolean enabled);
}
