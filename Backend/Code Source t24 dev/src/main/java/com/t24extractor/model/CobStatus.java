package com.t24extractor.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * État de la clôture journalière T24 (COB, <i>Close Of Business</i>).
 *
 * <p>L'extraction ne doit pas tourner pendant le COB : les tables sont alors en
 * cours de mise à jour et les données lues seraient incohérentes. La valeur
 * {@code STOP} signifie que la clôture est terminée et que l'extraction peut
 * démarrer.
 *
 * @param recid          identifiant de la ligne de service, toujours {@code COB}
 * @param serviceControl état courant du service
 * @see com.t24extractor.tasklet.CobStatusTasklet
 */
@Table("F_TSA_SERVICE")
public record CobStatus(
        @Id
        @Column("RECID")
        String recid,
        @Column("SERVICE_CONTROL")
        String serviceControl
) {
}
