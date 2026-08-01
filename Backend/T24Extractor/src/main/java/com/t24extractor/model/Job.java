package com.t24extractor.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Résumé d'une exécution de job, destiné au suivi.
 *
 * <p>Vue applicative des métadonnées Spring Batch : statut, durée et volumes
 * lus/écrits.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Getter
@Setter
public class Job {
	private Long jobId ;
    private String status ;
    private String duration ;
    private String name ;
    private Integer read ;
    private Integer write ;
}
