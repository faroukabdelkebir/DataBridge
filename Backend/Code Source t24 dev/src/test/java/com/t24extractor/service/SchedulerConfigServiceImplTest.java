package com.t24extractor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.t24extractor.exception.InvalidSchedulerConfigException;
import com.t24extractor.exception.SchedulerConfigNotFoundException;
import com.t24extractor.model.EnabledUpdateResult;
import com.t24extractor.model.SchedulerUpdateResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Règles métier de la planification, désormais portées par le service.
 *
 * Le service ne connaît pas HTTP : il valide, persiste, et signale les refus
 * par des exceptions métier que la couche web traduit en codes de statut.
 */
class SchedulerConfigServiceImplTest {

    private static final String UPDATE_FLAG = "UPDATE BATCH_CONFIG SET scheduler = ? WHERE id = ?";
    private static final String UPDATE_FLAG_AND_TIME =
            "UPDATE BATCH_CONFIG SET scheduler = ?, scheduler_time = ? WHERE id = ?";
    private static final String UPDATE_ENABLED = "UPDATE BATCH_CONFIG SET enabled = ? WHERE id = ?";

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final SchedulerConfigServiceImpl service = new SchedulerConfigServiceImpl(jdbcTemplate);

    // ── Lecture ──────────────────────────────────────────────────────────────

    @Test
    void mapsDatabaseRowsToFrontendDto() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(row()));

        List<Map<String, Object>> configs = service.findAllConfigs();

        assertEquals(1, configs.size());
        Map<String, Object> dto = configs.get(0);
        assertEquals("CUSTOMER", dto.get("tableName"));
        assertEquals("SS.CUSTOMER", dto.get("t24SelectionName"));
        assertEquals("DATE_TIME", dto.get("colonneRef"));
        assertEquals("Full", dto.get("chargement"));
        assertEquals(20260101L, dto.get("lastDate"));
        assertEquals(20250101L, dto.get("minDate"));
        assertEquals("06:30", dto.get("schedulerTime"));
        // Les colonnes bit/int de SQL Server doivent ressortir en booléens JSON.
        assertEquals(Boolean.TRUE, dto.get("enabled"));
        assertEquals(Boolean.FALSE, dto.get("scheduler"));
        // Les clés SQL brutes ne doivent pas fuiter vers le frontend.
        assertFalse(dto.containsKey("TableName"));
        assertFalse(dto.containsKey("scheduler_time"));
    }

    @Test
    void convertsBooleanAndNullFlagsFromEitherColumnType() {
        Map<String, Object> row = row();
        row.put("enabled", Boolean.TRUE);
        row.put("scheduler", null);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(row));

        Map<String, Object> dto = service.findAllConfigs().get(0);

        assertEquals(Boolean.TRUE, dto.get("enabled"));
        assertEquals(Boolean.FALSE, dto.get("scheduler"));
    }

    // ── Planification ────────────────────────────────────────────────────────

    @Test
    void updatesSchedulerFlagAndTimeTogether() {
        when(jdbcTemplate.update(UPDATE_FLAG_AND_TIME, 1, "06:30", "1")).thenReturn(1);

        SchedulerUpdateResult result = service.updateScheduler("1", true, "06:30");

        assertEquals(new SchedulerUpdateResult("1", true, "06:30", true), result);
    }

    @Test
    void togglesSchedulerOffWithoutTouchingStoredTime() {
        when(jdbcTemplate.update(UPDATE_FLAG, 0, "1")).thenReturn(1);

        SchedulerUpdateResult result = service.updateScheduler("1", false, null);

        assertEquals(new SchedulerUpdateResult("1", false, "", true), result);
        // Désactiver ne doit jamais écraser l'heure déjà enregistrée.
        verify(jdbcTemplate, never()).update(eq(UPDATE_FLAG_AND_TIME), anyArg(), anyArg(), anyArg());
    }

    @Test
    void rejectsMalformedSchedulerTime() {
        for (String invalid : List.of("6:30", "24:00", "06:60", "0630", "not-a-time", "06:30:00")) {
            InvalidSchedulerConfigException error = assertThrows(InvalidSchedulerConfigException.class,
                    () -> service.updateScheduler("1", true, invalid), invalid);
            assertEquals("schedulerTime doit être au format HH:mm (ex. 06:30)", error.getMessage());
        }
        // La validation précède tout accès base : aucune requête ne doit partir.
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void acceptsSchedulerTimeBoundaries() {
        when(jdbcTemplate.update(eq(UPDATE_FLAG_AND_TIME), anyArg(), anyArg(), anyArg())).thenReturn(1);

        for (String valid : List.of("00:00", "23:59", "09:05", "19:09")) {
            assertEquals(valid, service.updateScheduler("1", true, valid).schedulerTime(), valid);
        }
    }

    @Test
    void refusesToEnableSchedulingWhenNoTimeIsKnown() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("1"))).thenReturn(List.of());

        InvalidSchedulerConfigException error = assertThrows(InvalidSchedulerConfigException.class,
                () -> service.updateScheduler("1", true, null));

        assertEquals("Aucune heure définie : fournissez schedulerTime (HH:mm)", error.getMessage());
        verify(jdbcTemplate, never()).update(eq(UPDATE_FLAG), anyArg(), anyArg());
    }

    @Test
    void enablesSchedulingWhenTimeAlreadyStored() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("1"))).thenReturn(List.of("06:30"));
        when(jdbcTemplate.update(UPDATE_FLAG, 1, "1")).thenReturn(1);

        assertEquals(new SchedulerUpdateResult("1", true, "", true),
                service.updateScheduler("1", true, null));
    }

    @Test
    void treatsBlankSchedulerTimeAsAbsent() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("1"))).thenReturn(List.of("  "));

        assertThrows(InvalidSchedulerConfigException.class,
                () -> service.updateScheduler("1", true, "   "));
    }

    @Test
    void reportsUnknownSchedulerConfigId() {
        when(jdbcTemplate.update(eq(UPDATE_FLAG_AND_TIME), anyArg(), anyArg(), anyArg())).thenReturn(0);

        assertThrows(SchedulerConfigNotFoundException.class,
                () -> service.updateScheduler("999", true, "06:30"));
    }

    // ── Activation ───────────────────────────────────────────────────────────

    @Test
    void togglesEnabledFlag() {
        when(jdbcTemplate.update(UPDATE_ENABLED, 1, "1")).thenReturn(1);

        assertEquals(new EnabledUpdateResult("1", true, true), service.updateEnabled("1", true));
    }

    @Test
    void disablesExtraction() {
        when(jdbcTemplate.update(UPDATE_ENABLED, 0, "1")).thenReturn(1);

        assertEquals(new EnabledUpdateResult("1", false, true), service.updateEnabled("1", false));
    }

    @Test
    void reportsUnknownIdOnEnabledToggle() {
        assertThrows(SchedulerConfigNotFoundException.class, () -> service.updateEnabled("999", false));

        verify(jdbcTemplate).update(UPDATE_ENABLED, 0, "999");
    }

    // ── Utilitaires de test ──────────────────────────────────────────────────

    /**
     * Force la résolution vers {@code update(String, Object...)} : un simple
     * {@code any()} ferait choisir au compilateur la surcharge
     * {@code update(String, Object[], int[])}, et le stub ne s'appliquerait pas.
     */
    private static Object anyArg() {
        return ArgumentMatchers.<Object>any();
    }

    private Map<String, Object> row() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "1");
        row.put("TableName", "CUSTOMER");
        row.put("T24SelectionName", "SS.CUSTOMER");
        row.put("colonne_ref", "DATE_TIME");
        row.put("chargement", "Full");
        // last_date / min_date sont des BIGINT : le driver renvoie des Long.
        row.put("last_date", 20260101L);
        row.put("min_date", 20250101L);
        row.put("enabled", 1);
        row.put("scheduler", 0);
        row.put("scheduler_time", "06:30");
        return row;
    }
}
