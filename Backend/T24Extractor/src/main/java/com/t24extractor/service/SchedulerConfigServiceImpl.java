package com.t24extractor.service;

import com.t24extractor.exception.InvalidSchedulerConfigException;
import com.t24extractor.exception.SchedulerConfigNotFoundException;
import com.t24extractor.model.EnabledUpdateResult;
import com.t24extractor.model.SchedulerUpdateResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.t24extractor.commons.Constants.SELECT_BATCH_CONFIGS;
import static com.t24extractor.commons.Constants.SELECT_SCHEDULER_TIME;
import static com.t24extractor.commons.Constants.UPDATE_BATCH_ENABLED;
import static com.t24extractor.commons.Constants.UPDATE_SCHEDULER;
import static com.t24extractor.commons.Constants.UPDATE_SCHEDULER_AND_TIME;

/**
 * Implémentation adossée à BATCH_CONFIG.
 *
 * <p>L'accès passe par {@code JdbcTemplate} plutôt que par un dépôt : les
 * colonnes de cette table sont hétérogènes et lues telles quelles pour le
 * frontend, sans mapping vers une entité.
 *
 * <p>Deux règles gouvernent la planification :
 * <ul>
 *   <li>l'heure doit respecter {@code HH:mm}, format attendu par
 *       {@link com.t24extractor.scheduler.AutoExtractionScheduler} ;</li>
 *   <li>activer la planification suppose qu'une heure soit connue — fournie
 *       dans la demande, ou déjà enregistrée.</li>
 * </ul>
 */
@Service
public class SchedulerConfigServiceImpl implements ISchedulerConfigService {

    /** Format attendu par LocalTime.parse dans l'AutoExtractionScheduler. */
    private static final Pattern HHMM = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private final JdbcTemplate jdbcBI;

    public SchedulerConfigServiceImpl(@Qualifier("jdbcBI") JdbcTemplate jdbcBI) {
        this.jdbcBI = jdbcBI;
    }

    @Override
    public List<Map<String, Object>> findAllConfigs() {
        return jdbcBI.queryForList(SELECT_BATCH_CONFIGS).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public SchedulerUpdateResult updateScheduler(String id, boolean scheduler, String rawSchedulerTime) {
        String schedulerTime = normalizeTime(rawSchedulerTime);

        if (schedulerTime != null && !HHMM.matcher(schedulerTime).matches()) {
            throw new InvalidSchedulerConfigException(
                    "schedulerTime doit être au format HH:mm (ex. 06:30)");
        }

        // Activer la planification exige une heure : celle fournie, ou une déjà en base
        if (scheduler && schedulerTime == null && !hasSchedulerTime(id)) {
            throw new InvalidSchedulerConfigException(
                    "Aucune heure définie : fournissez schedulerTime (HH:mm)");
        }

        int updated = schedulerTime == null
                ? jdbcBI.update(UPDATE_SCHEDULER, scheduler ? 1 : 0, id)
                : jdbcBI.update(UPDATE_SCHEDULER_AND_TIME, scheduler ? 1 : 0, schedulerTime, id);

        if (updated == 0) {
            throw new SchedulerConfigNotFoundException(id);
        }
        return new SchedulerUpdateResult(id, scheduler, schedulerTime == null ? "" : schedulerTime, true);
    }

    @Override
    public EnabledUpdateResult updateEnabled(String id, boolean enabled) {
        int updated = jdbcBI.update(UPDATE_BATCH_ENABLED, enabled ? 1 : 0, id);
        if (updated == 0) {
            throw new SchedulerConfigNotFoundException(id);
        }
        return new EnabledUpdateResult(id, enabled, true);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Une heure vide ou blanche équivaut à « non fournie ». */
    private String normalizeTime(String rawSchedulerTime) {
        if (rawSchedulerTime == null) {
            return null;
        }
        String trimmed = rawSchedulerTime.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasSchedulerTime(String id) {
        List<String> times = jdbcBI.queryForList(SELECT_SCHEDULER_TIME, String.class, id);
        return !times.isEmpty() && times.get(0) != null && !times.get(0).isBlank();
    }

    /** Aligne les clés JSON sur le modèle BatchConfigRecord du frontend. */
    private Map<String, Object> toDto(Map<String, Object> row) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", row.get("id"));
        dto.put("tableName", row.get("TableName"));
        dto.put("t24SelectionName", row.get("T24SelectionName"));
        dto.put("colonneRef", row.get("colonne_ref"));
        dto.put("chargement", row.get("chargement"));
        dto.put("lastDate", row.get("last_date"));
        dto.put("minDate", row.get("min_date"));
        dto.put("enabled", toBoolean(row.get("enabled")));
        dto.put("scheduler", toBoolean(row.get("scheduler")));
        dto.put("schedulerTime", row.get("scheduler_time"));
        return dto;
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return false;
    }
}
