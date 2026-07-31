package com.t24extractor.model;

import lombok.*;
import org.springframework.data.annotation.Id;

/**
 * Un enregistrement T24 brut, tel que lu sur Oracle.
 *
 * <p>C'est l'unité lue par le reader et transmise au
 * {@link com.t24extractor.processor.XMLProcessor}, qui la décode en JSON.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class T24Table
{
    @Id
    private String recid ;
    private String xmlRecord ;
}
