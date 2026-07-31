package com.t24extractor.bi_sync.ops;


import com.t24extractor.model.MetaDataTable;
import com.t24extractor.model.SchemaSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static com.t24extractor.commons.Constants.COUNT_ALL_ROWS_TPL;
import static com.t24extractor.commons.Constants.DROP_TABLE_TPL;
import static com.t24extractor.commons.Constants.INSERT_METADATA_IF_ABSENT_TPL;
import static com.t24extractor.commons.Constants.TRUNCATE_TABLE_TPL;
import static com.t24extractor.commons.Constants.UPDATE_TABLE_TPL;

/**
 * Contrat de génération SQL, indépendant du SGBD.
 *
 * <p>Regroupe ce qui ne dépend pas du dialecte (placeholders, typage ODS →
 * VARCHAR, colonnes obligatoires) et délègue le reste aux implémentations.
 *
 * <p>Le SQL produit ici est un livrable : l'échappement des identifiants,
 * l'ordre des colonnes et le nombre de paramètres conditionnent directement
 * l'intégrité des données insérées.
 *
 * @see SqlServerOps
 * @see OracleOps
 */
@Slf4j
public abstract class SqlOps {

    /**
     * @param jdbcTemplate accès à la base cible
     * @param table        table cible
     * @return les colonnes métier existantes, hors colonnes techniques
     */
    public abstract Set<String> destColumns(JdbcTemplate jdbcTemplate, String table);

    /**
     * Crée la table de dépôt brut associée, si elle n'existe pas.
     *
     * @param jdbcTemplate accès à la base cible
     * @param table        table cible, préfixée à la création
     */
    public abstract void createRawTable(JdbcTemplate jdbcTemplate, String table);

    /**
     * @param table table à tester
     * @return un fragment SQL conditionnant l'instruction suivante à l'existence
     *         de la table
     */
    public abstract String existsQuery(String table);

    /**
     * Crée la table cible avec ses colonnes techniques et métier, si elle
     * n'existe pas déjà.
     *
     * @param jdbcTemplate accès à la base cible
     * @param table        table cible
     * @param set          colonnes métier à créer
     */
    public abstract void createDestTable(JdbcTemplate jdbcTemplate, String table, Set<SchemaSource> set);

    /**
     * Crée la table cible réduite à ses seules colonnes techniques.
     *
     * @param jdbcTemplate accès à la base cible
     * @param table        table cible
     */
    public abstract void createDefaultDestTable(JdbcTemplate jdbcTemplate, String table);

    /**
     * @param table table cible
     * @param set   colonnes métier, dans l'ordre d'insertion
     * @return l'INSERT paramétré, colonnes techniques comprises
     */
    public abstract String insertConstruction(String table, Set<SchemaSource> set);

    /**
     * @param table table cible
     * @param set   colonnes à mettre à jour
     * @return un MERGE rapprochant la table de sa version de travail sur {@code _id}
     */
    public abstract String mergeConstruction(String table, Set<SchemaSource> set);

    /**
     * @param jdbcTemplate accès à la base cible
     * @param table        table cible
     * @param column       colonne à ajouter
     * @param dataType     type ODS, converti en type SQL
     */
    public abstract void addCol(JdbcTemplate jdbcTemplate, String table, String column, String dataType);

    /**
     * @param jdbcTemplate accès à la base cible
     * @param table        table cible
     * @param col          colonne à supprimer
     */
    public abstract void dropCol(JdbcTemplate jdbcTemplate, String table, String col);

    /**
     * @param jdbcTemplate accès à la base cible
     * @param table        table à vider
     */
    public void truncateTable(JdbcTemplate jdbcTemplate, String table) {
        jdbcTemplate.execute(String.format(TRUNCATE_TABLE_TPL, escapeTableName(table)));
    }

    /**
     * @param jdbcTemplate accès à la base cible
     * @param table        table à supprimer
     */
    public void dropTable(JdbcTemplate jdbcTemplate, String table) {
        jdbcTemplate.execute(String.format(DROP_TABLE_TPL, escapeTableName(table)));
    }

    /**
     * @param set colonnes métier
     * @return les définitions {@code "colonne type"} pour un CREATE TABLE
     */
    public Set<String> fieldsOnly(Set<SchemaSource> set) {
        Set<String> tset = new LinkedHashSet<>();
        for (SchemaSource bi : set) {
            String obj = String.join(" ", escapeColumn(bi.getColumnName()), getDataType(bi.getDataType()));
            tset.add(obj);
        }
        return tset;
    }


    /**
     * Liste des colonnes de l'INSERT, colonnes techniques en tête.
     *
     * <p>L'ordre fait foi : c'est celui dans lequel
     * {@link com.t24extractor.preparedstatementsetter.ItemPrepStmtSetter}
     * positionnera les paramètres.
     *
     * @param set colonnes métier
     * @return {@code _id}, {@code ORIGINAL_ID}, puis les colonnes métier
     */
    public List<String> fieldsToInsert(Set<SchemaSource> set) {
        List<String> mandatoryFields = new ArrayList<>();
        mandatoryFields.add("_id");
        mandatoryFields.add("ORIGINAL_ID");

        log.info("*****************************************");
        log.info("Info {} ",set
                .stream()
                .map(SchemaSource::getColumnName)
                .toList());

        mandatoryFields.addAll(set
                .stream()
                .map(SchemaSource::getColumnName)
                .toList());
        return mandatoryFields;
    }

    /**
     * Variante échappée de {@link #fieldsToInsert(Set)}, pour insertion directe
     * dans le SQL.
     *
     * @param set colonnes métier
     * @return les mêmes colonnes, avec leurs délimiteurs
     */
    public List<String> mssqlfieldsToInsert(Set<SchemaSource> set) {
        List<String> mandatoryFields = new ArrayList<>();
        mandatoryFields.add(escapeColumn("_id"));
        mandatoryFields.add(escapeColumn("ORIGINAL_ID"));
        mandatoryFields.addAll(set
                .stream()
                .map(c -> escapeColumn(c.getColumnName()))
                .toList());
        return mandatoryFields;
    }

    /**
     * @param fieldsNum nombre de colonnes
     * @return autant de {@code ?} séparés par des virgules ; jamais vide
     */
    public String placeHolders(int fieldsNum) {
        return "?,".repeat(Math.max(0, fieldsNum - 1)) +
                "?";
    }

    /**
     * Traduit un type ODS en type SQL.
     *
     * <p>Tout est stocké en texte : la conversion en types métier est faite en
     * aval, dans l'entrepôt. Seule distinction, les champs multivalués — dont
     * le type contient {@code StringMAX} — reçoivent une capacité illimitée car
     * ils portent un tableau JSON.
     *
     * @param ssdataType type ODS issu du STANDARD.SELECTION
     * @return le type SQL correspondant
     */
    public String getDataType(String ssdataType) {
        if (ssdataType.contains("StringMAX")) {
            return " VARCHAR(MAX) ";
        }
        return "VARCHAR(8000)";
    }



    /**
     * Teste l'<b>absence</b> d'un index.
     *
     * <p>Le nom est trompeur : la méthode renvoie {@code true} lorsque l'index
     * n'existe <i>pas</i>. C'est ce que consomme {@link #createIndex}.
     *
     * @param jdbcTemplate accès à la base cible
     * @param tableName    table portant l'index
     * @param column       nom de l'index recherché
     * @return {@code true} si l'index est absent
     */
    public abstract boolean existIndexQuery(JdbcTemplate jdbcTemplate, String tableName, String column);

    /**
     * @param column nom de colonne
     * @return le nom délimité selon le dialecte
     */
    public abstract String escapeColumn(String column);

    /**
     * @param table nom de table
     * @return le nom délimité selon le dialecte
     */
    public abstract String escapeTableName(String table);

    /**
     * Crée l'index s'il n'existe pas déjà.
     *
     * @param jdbcTemplate accès à la base cible
     * @param tableName    table à indexer
     * @param column       colonne à indexer, qui donne aussi son nom à l'index
     */
    public abstract void createIndex(JdbcTemplate jdbcTemplate, String tableName, String column);

    /**
     * @param jdbcTemplate accès à la base cible
     * @param column       nom de l'index à supprimer
     */
    public abstract void dropIndex(JdbcTemplate jdbcTemplate, String column);

    /**
     * Déclenche la normalisation des champs JSON par procédure stockée.
     *
     * @param jdbcTemplate accès à la base cible
     * @param tableName    table à normaliser
     * @return le nombre de lignes traitées
     */
    public abstract int jsonNormalize(JdbcTemplate jdbcTemplate, String tableName);


    /**
     * Comptage plafonné, utilisé pour savoir si une table est simplement non vide
     * sans en payer le comptage complet.
     *
     * @param jdbcTemplate accès à la base
     * @param tableName    table à sonder
     * @return le nombre de lignes, au plus cinq
     */
    public abstract Long countTopFive(JdbcTemplate jdbcTemplate, String tableName);


    /**
     * Comptage exact des lignes.
     *
     * <p>Implémentation de repli : les sous-classes la redéfinissent pour
     * s'appuyer sur les statistiques du SGBD, bien moins coûteuses.
     *
     * @param jdbcTemplate accès à la base
     * @param table        table à compter
     * @return le nombre de lignes, ou {@code 0} en cas d'échec
     */
    public Long countAll(JdbcTemplate jdbcTemplate, String table) {
        Long count = 0L;
        try {

            count = jdbcTemplate.queryForObject(String.format(COUNT_ALL_ROWS_TPL, table), Long.class);
        } catch (Exception e) {
            log.error("Une erreur est survenue lors de l'exécution de la requete Count sur la table {}", table);
        }
        return count;
    }

    /**
     * Exécute un UPDATE composé dynamiquement.
     *
     * <p>Les valeurs sont concaténées telles quelles dans le SQL : les chaînes
     * doivent donc être fournies déjà quotées par l'appelant.
     *
     * @param jdbcTemplate accès à la base cible
     * @param table        table à mettre à jour
     * @param cols         colonnes et valeurs, dans l'ordre d'itération fourni
     * @param condition    clause WHERE, sans le mot-clé
     */
    public void updateQuery(JdbcTemplate jdbcTemplate, String table, Map<String, Object> cols, String condition) {
        StringBuilder assignments = new StringBuilder();
        Iterator<Map.Entry<String, Object>> it = cols.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> entry = it.next();
            assignments.append(entry.getKey()).append("=").append(entry.getValue());

            if (it.hasNext()) {
                assignments.append(", ");
            }
        }
        jdbcTemplate.batchUpdate(String.format(UPDATE_TABLE_TPL, table, assignments, condition));
    }

    /**
     * Enregistre les champs multivalués dans le registre des métadonnées, sans
     * doublon.
     *
     * @param jdbcTemplate      accès à la base cible
     * @param metaDataTableList champs multivalués à déclarer
     */
    public void save(JdbcTemplate jdbcTemplate, List<MetaDataTable> metaDataTableList) {


        /* IF EXISTS(SELECT * from iplog Where [date]='20120101' and sip=1 and dip=2 and port=80)
         BEGIN
                 UPDATE
         iplog
                 SET
         t.size=t.size+1
         WHERE
                 [date]='20120101' and sip=1 and dip=2 and port=80
         END
                 ELSE
         BEGIN
         INSERT INTO iplog VALUES ('20120101',1,2,80,1)
         END */


        for (MetaDataTable meta : metaDataTableList) {
            jdbcTemplate.batchUpdate(String.format(INSERT_METADATA_IF_ABSENT_TPL,
                    meta.tablename(), meta.multivalueFields(), meta.localref()));
        }



    }
}
