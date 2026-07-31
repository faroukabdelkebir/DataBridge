package wevioo.se.monitoringms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import wevioo.se.monitoringms.dto.ReconciliationDto;
import wevioo.se.monitoringms.model.StatsRecord;
import wevioo.se.monitoringms.repository.StatsRepository;

import java.util.List;

/**
 * Réconciliation des comptages Oracle ↔ SQL Server.
 *
 * <p>Pour chaque table extraite, le nombre de lignes comptées côté source
 * avant extraction est comparé au nombre de lignes réellement insérées côté
 * cible. Le taux obtenu est qualifié selon trois paliers :</p>
 * <ul>
 *   <li>« Conforme » — taux supérieur ou égal à {@code 100 − tolérance} ;</li>
 *   <li>« Avertissement » — taux compris entre 80 % et ce seuil ;</li>
 *   <li>« Non conforme » — taux inférieur à 80 %.</li>
 * </ul>
 *
 * <p>La tolérance est configurable via
 * {@code monitor.alert.reconciliation-tolerance-pct}, le palier
 * d'avertissement restant fixé à 80 %.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    /** Accès aux comptages produits par le microservice d'extraction. */
    private final StatsRepository statsRepo;

    /** Écart maximal toléré, en points de pourcentage. */
    @Value("${monitor.alert.reconciliation-tolerance-pct:5}")
    private double tolerancePct;

    /**
     * Retourne le dernier état de réconciliation de chaque table.
     *
     * <p>Partage le cache des indicateurs, avec une durée de vie de deux
     * minutes.</p>
     *
     * @return un état par table extraite
     */
    @Cacheable(value = "kpis", key = "'reconciliation'")
    public List<ReconciliationDto> getLatestReconciliation() {
        List<StatsRecord> latestStats = statsRepo.findLatestPerTable();

        return latestStats.stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Retourne l'historique de réconciliation d'une table.
     *
     * <p>La profondeur est bornée aux trente derniers comptages.</p>
     *
     * @param tableName nom de la table SQL Server
     * @return les états du plus récent au plus ancien ; liste vide si la table
     *         est inconnue
     */
    public List<ReconciliationDto> getReconciliationForTable(String tableName) {
        return statsRepo.findByTable(tableName, 30).stream()
                .map(this::toDto)
                .toList();
    }

    // ── Conversion ────────────────────────────────────────────────────────────

    /**
     * Convertit un comptage brut en état de réconciliation qualifié.
     *
     * <p>Une source vide est traitée comme un taux de 100 % : il n'y avait
     * rien à charger, l'extraction n'a donc rien manqué.</p>
     *
     * @param s comptage produit par l'extraction
     * @return l'état correspondant, taux arrondi au centième
     */
    private ReconciliationDto toDto(StatsRecord s) {
        int    delta  = s.getOracleCount() - s.getSqlCount();
        double rate   = s.getOracleCount() == 0 ? 100.0
                : (double) s.getSqlCount() / s.getOracleCount() * 100.0;
        String status = resolveStatus(rate);

        return ReconciliationDto.builder()
                .tableName(s.getMssqlTable())
                .businessDate(s.getBusinessDate())
                .oracleCount(s.getOracleCount())
                .sqlServerCount(s.getSqlCount())
                .delta(delta)
                .reconciliationRate(Math.round(rate * 100.0) / 100.0)
                .reconciled(rate >= (100.0 - tolerancePct))
                .status(status)
                .build();
    }

    /**
     * Qualifie un taux de réconciliation.
     *
     * @param rate taux en pourcentage
     * @return « Conforme », « Avertissement » ou « Non conforme »
     */
    private String resolveStatus(double rate) {
        if (rate >= (100.0 - tolerancePct)) return "Conforme";
        if (rate >= 80.0)                   return "Avertissement";
        return "Non conforme";
    }
}

