package com.t24extractor.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Classification T24 d'un fichier, qui détermine son préfixe de table.
 *
 * <p>Une table classée {@code INT} est un fichier interne, préfixé {@code F} ;
 * les autres sont des fichiers métier préfixés {@code FBNK}. Ce préfixe est
 * nécessaire pour interroger TAFJ_VOC.
 *
 * @param recid          nom de la table T24, sans suffixe {@code $HIS}/{@code $NAU}
 * @param suffixes       suffixes déclarés pour ce fichier
 * @param classification classification T24 ({@code INT} ou autre)
 * @see com.t24extractor.tasklet.FileControlServiceImpl
 */
@Table("F_FILE_CONTROL")
public record FileControl(
        @Id
        @Column("RECID")
        String recid,

        @Column("SUFFIXES")
        String suffixes,

        @Column("CLASSIFICATION")
        String classification
){}
