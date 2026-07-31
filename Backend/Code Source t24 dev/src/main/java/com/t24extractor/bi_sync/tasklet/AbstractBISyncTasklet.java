package com.t24extractor.bi_sync.tasklet;


import com.t24extractor.model.SchemaSource;
import org.json.JSONObject;

import java.util.List;


/**
 * Opérations d'alignement du schéma BI sur le schéma T24.
 *
 * <p>Les méthodes prenant un paramètre {@code diff} appliquent un écart calculé
 * depuis la table d'audit : {@code +1} pour un ajout, {@code -1} pour une
 * suppression, {@code 0} pour un statu quo.
 *
 * @see BISyncTasklet
 * @see com.t24extractor.model.SchemaSourceAudit
 */
public interface AbstractBISyncTasklet {

    /**
     * @param tableName table BI
     * @return {@code true} si le schéma de la table est déjà connu ;
     *         {@code false} pour une table jamais chargée
     */
    boolean statusSchemaSource(String tableName);

    /**
     * Déclare les champs multivalués de la table dans le registre des métadonnées.
     *
     * @param tableName        table BI
     * @param schemaSourceList colonnes de la table
     */
    void loadMetaDataTable(String tableName, List<SchemaSource> schemaSourceList);

    /**
     * @param tableName table BI dont on consulte les changements en attente
     */
    void checkSchemaUpdate(String tableName);

    /**
     * @param tableName table BI concernée par une opération différée
     */
    void scheduleOp(String tableName);

    /**
     * Crée ou supprime un index selon l'écart demandé.
     *
     * <p>Sur une table volumineuse, la création est différée plutôt qu'exécutée
     * en ligne, pour ne pas la bloquer pendant l'extraction.
     *
     * @param tableName table BI
     * @param diff      {@code +1} pour créer, {@code -1} pour supprimer
     * @param column    colonne à indexer
     */
    void updateIndex(String tableName, int diff, String column);


    /**
     * Ajoute ou supprime une colonne selon l'écart demandé.
     *
     * @param table    table BI
     * @param diff     {@code +1} pour ajouter, {@code -1} pour supprimer
     * @param column   colonne concernée
     * @param dataType type ODS de la colonne
     */
    void updateColumn(String table, int diff, String column, String dataType);

    /**
     * @param tableName table BI
     * @param ss        dictionnaire STANDARD.SELECTION de la table
     */
    void loadSchemaSource(String tableName, JSONObject ss);

    /**
     * Restreint le dictionnaire aux seules colonnes actives de la table.
     *
     * <p>Garantit que mapping XML, liste de colonnes et INSERT reposent sur le
     * même ensemble : une colonne active mais absente du STANDARD.SELECTION
     * entrerait sinon dans l'INSERT sans jamais être alimentée.
     *
     * @param tableName table BI
     * @param ssMap     dictionnaire d'origine ; <b>vidé</b> par l'appel
     * @return un nouveau dictionnaire filtré
     */
    JSONObject ssMapClean(String tableName, JSONObject ssMap);

}
