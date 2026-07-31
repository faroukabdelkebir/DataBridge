package wevioo.se.monitoringms.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Indicateurs de performance agrégés sur une fenêtre glissante.
 *
 * <p>Toutes les valeurs portent sur les {@link #windowDays} derniers jours.
 * Les cartes indexées par traitement utilisent le nom logique du traitement
 * comme clé ({@code T24}, {@code CUSTOMER.JOB}…).</p>
 *
 * @see wevioo.se.monitoringms.service.KpiService
 */
@Data
@Builder
public class KpiDto {

    /** Largeur de la fenêtre d'analyse, en jours. */
    private int    windowDays;

    // ── Taux de succès ──────────────────────────────

    /** Taux de succès global sur la fenêtre, en pourcentage arrondi au centième. */
    private double overallSuccessRate;

    /** Taux de succès par traitement, en pourcentage. */
    private Map<String, Double> successRateByJob;

    // ── Volume d'extraction ──────────────────────────

    /** Nombre total de lignes extraites sur la fenêtre. */
    private long   totalRowsExtracted;

    /** Lignes extraites par table cible. */
    private Map<String, Long> rowsByTable;

    // ── Débit (throughput) ──────────────────────────

    /** Débit moyen en lignes par seconde, arrondi au centième. */
    private double avgThroughputPerSec;

    /** Débit moyen par traitement, en lignes par seconde. */
    private Map<String, Double> throughputByJob;

    // ── Durées moyennes ─────────────────────────────

    /** Durée moyenne d'une exécution, en secondes arrondies au dixième. */
    private double avgDurationSeconds;

    /** Durée moyenne par traitement, en secondes. */
    private Map<String, Double> avgDurationByJob;

    // ── COB & Business Date ─────────────────────────

    /** Dernière date métier traitée avec succès. */
    private String lastSuccessfulBusinessDate;

    /** Nombre d'exécutions réussies consécutives. */
    private int    consecutiveSuccessCount;

    // ── Erreurs ──────────────────────────────────────

    /** Nombre total d'échecs sur la fenêtre. */
    private int    failureCount;

    /** Plus longue série d'échecs consécutifs observée, tous traitements confondus. */
    private int    consecutiveFailureCount;

    /** Message du dernier échec rencontré. */
    private String lastFailureMessage;

    // ── Tendance ────────────────────────────────────

    /** Volumétrie jour par jour sur la fenêtre, du plus récent au plus ancien. */
    private List<DailyVolumeDto> volumeTrend;

    /** Instant de calcul des indicateurs, utile pour interpréter les données mises en cache. */
    private LocalDateTime computedAt;
}
