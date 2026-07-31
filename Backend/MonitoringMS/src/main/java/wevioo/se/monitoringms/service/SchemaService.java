package wevioo.se.monitoringms.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import wevioo.se.monitoringms.config.CacheConfig;
import wevioo.se.monitoringms.model.SchemaAuditRecord;
import wevioo.se.monitoringms.model.SchemaSourceRecord;
import wevioo.se.monitoringms.repository.SchemaRepository;

import java.util.List;

/**
 * Exploration du dictionnaire de schéma T24 et de son historique d'évolution.
 *
 * <p>S'appuie sur {@code SCHEMA_SOURCE}, qui décrit les colonnes extraites, et
 * sur {@code SCHEMA_SOURCE_AUDIT}, alimentée par un déclencheur à chaque
 * modification.</p>
 *
 * <p>Les consultations du dictionnaire sont mises en cache dix minutes ; les
 * méthodes d'audit ne le sont pas, la détection de dérive devant refléter
 * l'état courant.</p>
 */
@Service
@RequiredArgsConstructor
public class SchemaService {

    /** Accès au dictionnaire de schéma et à son audit. */
    private final SchemaRepository schemaRepo;

    // ── Schema source ─────────────────────────────────────────────────────────

    /**
     * Retourne l'intégralité du dictionnaire de schéma.
     *
     * @return toutes les colonnes connues, triées par table puis par colonne
     */
    @Cacheable(value = CacheConfig.CACHE_SCHEMA, key = "'all'")
    public List<SchemaSourceRecord> getAllColumns() {
        return schemaRepo.findAll();
    }

    /**
     * Retourne les colonnes déclarées pour une table T24.
     *
     * <p>Chaque table dispose de sa propre entrée de cache.</p>
     *
     * @param tableName nom de la table T24
     * @return les colonnes de la table ; liste vide si elle est absente du
     *         dictionnaire
     */
    @Cacheable(value = CacheConfig.CACHE_SCHEMA, key = "'table_' + #tableName")
    public List<SchemaSourceRecord> getColumnsByTable(String tableName) {
        return schemaRepo.findByTable(tableName);
    }

    /**
     * Résume la couverture du dictionnaire, table par table.
     *
     * @return un résumé par table T24, trié par nom de table
     */
    @Cacheable(value = CacheConfig.CACHE_SCHEMA, key = "'summary'")
    public List<SchemaRepository.TableSchemaSummary> getSchemaSummary() {
        return schemaRepo.summarizeByTable();
    }

    // ── Audit ────────────────────────────────────────────────────────────────

    /**
     * Retourne les dernières modifications de schéma, toutes tables confondues.
     *
     * @param limit nombre maximal d'événements à ramener
     * @return les événements du plus récent au plus ancien
     */
    public List<SchemaAuditRecord> getRecentAuditEvents(int limit) {
        return schemaRepo.findRecentAudit(limit);
    }

    /**
     * Retourne l'historique des modifications d'une table.
     *
     * @param tableName nom de la table T24
     * @return les événements du plus récent au plus ancien ; liste vide si la
     *         table n'a jamais été modifiée
     */
    public List<SchemaAuditRecord> getAuditByTable(String tableName) {
        return schemaRepo.findAuditByTable(tableName);
    }

    /**
     * Compte les dérives de schéma survenues récemment.
     *
     * @param days profondeur de la fenêtre, en jours
     * @return le nombre de modifications sur la période
     */
    public int countRecentDrifts(int days) {
        return schemaRepo.countRecentDrifts(days);
    }
}

