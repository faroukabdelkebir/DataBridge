package com.t24extractor.model;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import static com.t24extractor.commons.Constants.T24_METADATA;

/**
 * Déclaration d'un champ multivalué éclaté en table satellite.
 *
 * <p>Chaque champ multivalué d'une table BI donne lieu à une table
 * {@code TABLE#CHAMP}. Cette entité en tient le registre, afin que les
 * consommateurs BI sachent quelles tables satellites existent.
 *
 * <p>L'égalité par valeur est utilisée pour la suppression : désactiver une
 * colonne supprime la métadonnée reconstruite à l'identique.
 *
 * @param tablename        table BI porteuse du champ
 * @param multivalueFields nom du champ multivalué
 * @param localref         {@code true} s'il s'agit d'un champ LOCAL.REF
 */
@Table(value = T24_METADATA)
public record MetaDataTable(
        @Column("Table_Name") String tablename,
        @Column("MultiValueFields") String multivalueFields,
        @Column("LOCAL_REF") boolean localref) {
}
