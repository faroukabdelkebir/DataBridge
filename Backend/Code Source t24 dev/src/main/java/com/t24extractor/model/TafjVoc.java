package com.t24extractor.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Correspondance entre un nom de fichier T24 et sa table Oracle physique.
 *
 * <p>TAFJ_VOC est le dictionnaire du middleware TAFJ : c'est lui qui indique où
 * une table T24 est réellement stockée, et sous quelle forme. Le mode de
 * stockage conditionne toute la suite du traitement — lecture directe du
 * XMLRECORD, ou passage par une vue éclatée en colonnes.
 *
 * @param recid        nom T24 qualifié, par exemple {@code FBNK.CUSTOMER}
 * @param isBlob       mode de stockage : {@code X} pour XML, {@code B} ou
 *                     {@code C} pour BLOB/CLOB
 * @param orclFileName table Oracle correspondante, par exemple
 *                     {@code FBNK_CUSTOMER}
 */
@Table("TAFJ_VOC")
public record TafjVoc(
        @Id
        @Column("RECID")
        String recid,
        @Column("ISBLOB")
        String isBlob,
        @Column("ORCLFILENAME")
        String orclFileName

) {
}
