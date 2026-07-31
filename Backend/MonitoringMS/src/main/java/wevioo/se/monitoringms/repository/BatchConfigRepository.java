package wevioo.se.monitoringms.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import wevioo.se.monitoringms.model.BatchConfigRecord;

import java.util.List;
import java.util.Optional;

/**
 * Accès à {@code BATCH_CONFIG}, table de paramétrage des extractions T24.
 *
 * <p>Seul repository du projet à exposer des écritures : le monitoring peut
 * activer ou désactiver une extraction, et enregistrer la borne atteinte après
 * un chargement réussi. Les autres colonnes restent du ressort du
 * microservice d'extraction.</p>
 *
 * @see wevioo.se.monitoringms.model.BatchConfigRecord
 */
@Repository
@RequiredArgsConstructor
public class BatchConfigRepository {

    /** Gabarit JDBC pointant sur la base décisionnelle SQL Server. */
    private final JdbcTemplate jdbc;

    /** Convertit une ligne de {@code BATCH_CONFIG} en modèle. */
    private static final RowMapper<BatchConfigRecord> CONFIG_MAPPER = (rs, row) ->
            BatchConfigRecord.builder()
                    .id(rs.getString("id"))
                    .tableName(rs.getString("TableName"))
                    .t24SelectionName(rs.getString("T24SelectionName"))
                    .colonneRef(rs.getString("colonne_ref"))
                    .chargement(rs.getString("chargement"))
                    .lastDate(rs.getObject("last_date", Long.class))
                    .minDate(rs.getObject("min_date", Long.class))
                    .enabled(rs.getBoolean("enabled"))
                    .scheduler(rs.getBoolean("scheduler"))
                    .schedulerTime(rs.getString("scheduler_time"))
                    .build();

    /**
     * Charge l'ensemble des configurations d'extraction.
     *
     * @return toutes les configurations, triées par identifiant
     */
    public List<BatchConfigRecord> findAll() {
        return jdbc.query(
                "SELECT id, TableName, T24SelectionName, colonne_ref, chargement, last_date, min_date, enabled, scheduler, scheduler_time FROM BATCH_CONFIG ORDER BY id",
                CONFIG_MAPPER);
    }

    /**
     * Recherche une configuration par son identifiant.
     *
     * @param id identifiant de la configuration, généralement le nom de la table
     * @return la configuration correspondante, ou {@link Optional#empty()} si
     *         l'identifiant est inconnu
     */
    public Optional<BatchConfigRecord> findById(String id) {
        List<BatchConfigRecord> results = jdbc.query(
                "SELECT id, TableName, T24SelectionName, colonne_ref, chargement, last_date, min_date, enabled, scheduler, scheduler_time FROM BATCH_CONFIG WHERE id = ?",
                CONFIG_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Enregistre la borne temporelle atteinte par une extraction.
     *
     * <p>Appelé après un chargement réussi, afin que le passage suivant
     * reprenne à partir de cette borne.</p>
     *
     * @param id       identifiant de la configuration
     * @param lastDate nouvelle borne, au format {@code AAAAMMJJ}
     * @return le nombre de lignes mises à jour : {@code 1} en cas de succès,
     *         {@code 0} si l'identifiant est inconnu
     */
    public int updateLastDate(String id, Long lastDate) {
        return jdbc.update("UPDATE BATCH_CONFIG SET last_date = ? WHERE id = ?", lastDate, id);
    }

    /**
     * Active ou désactive une extraction.
     *
     * <p>Le booléen est converti en entier, la colonne cible étant un
     * {@code bit} SQL Server.</p>
     *
     * @param id      identifiant de la configuration
     * @param enabled {@code true} pour activer l'extraction, {@code false} pour la suspendre
     * @return le nombre de lignes mises à jour : {@code 1} en cas de succès,
     *         {@code 0} si l'identifiant est inconnu
     */
    public int setEnabled(String id, boolean enabled) {
        return jdbc.update("UPDATE BATCH_CONFIG SET enabled = ? WHERE id = ?", enabled ? 1 : 0, id);
    }
}
