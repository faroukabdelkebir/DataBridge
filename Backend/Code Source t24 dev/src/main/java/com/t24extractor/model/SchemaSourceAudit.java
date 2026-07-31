package com.t24extractor.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import static com.t24extractor.commons.Constants.SCHEMA_SOURCE_AUDIT;

/**
 * Trace d'un changement demandé sur une colonne, à appliquer au prochain job.
 *
 * <p>Activer ou désactiver une colonne depuis l'IHM ne modifie pas la table BI
 * immédiatement : une ligne d'audit est écrite, et l'étape {@code BI_SYNC} la
 * traduit en {@code ALTER TABLE} au démarrage de l'extraction suivante, puis la
 * purge.
 *
 * <p>La décision se lit dans les écarts : {@code newSourceEnabled -
 * oldSourceEnabled} vaut {@code +1} pour un ajout de colonne et {@code -1} pour
 * une suppression ; il en va de même des champs d'indexation.
 *
 * @param sourceId          identifiant de la colonne dans SCHEMA_SOURCE
 * @param sourceTableName   table BI concernée
 * @param ColumnName        colonne concernée
 * @param dataType          type ODS de la colonne ; {@code MAX} signale un champ
 *                          multivalué nécessitant une table satellite
 * @param newSourceEnabled  activation demandée (0 ou 1)
 * @param oldSourceEnabled  activation précédente (0 ou 1)
 * @param newSourceIndexed  indexation demandée (0 ou 1)
 * @param oldSourceIndexed  indexation précédente (0 ou 1)
 * @param id                clé technique de la ligne d'audit
 * @see com.t24extractor.bi_sync.tasklet.BISyncTasklet
 */
@Table(SCHEMA_SOURCE_AUDIT)
public record SchemaSourceAudit(
        @Id
        @Column("id")
        int id,

        @Column("sourceId")
        String sourceId,

        @Column("sourceTable_name")
        String sourceTableName,

        @Column("sourceColumn_name")
        String ColumnName,
        @Column("sourceDataType")
        String dataType,
        @Column("newSource_enabled")
        int newSourceEnabled,
        @Column("oldSource_enabled")
        int oldSourceEnabled,

        @Column("newSourceIndexed")
        int newSourceIndexed,
        @Column("oldSourceIndexed")
        int oldSourceIndexed

) {
}
