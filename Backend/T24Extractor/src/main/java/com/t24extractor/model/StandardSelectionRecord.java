package com.t24extractor.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serializable;

/**
 * Dictionnaire des champs d'une table T24, au format XML.
 *
 * <p>Le STANDARD.SELECTION décrit chaque champ : nom, type, longueur et
 * caractère simple ou multivalué. C'est la source de vérité qui permet de
 * transformer un XMLRECORD opaque en colonnes nommées.
 *
 * @see com.t24extractor.tasklet.StandardSelectionTasklet
 */
@Table("F_STANDARD_SELECTION")
@Data
public class StandardSelectionRecord implements Serializable {
        @Id
        @Column("RECID")
        private String recid ;
        @Column("XMLRECORD")
        private String xmlRecord ;
}
