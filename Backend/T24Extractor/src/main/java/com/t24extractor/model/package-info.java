/**
 * Entités persistées et objets d'échange.
 *
 * <p>Trois familles cohabitent :
 * <ul>
 *   <li><b>Entités T24 (Oracle)</b> — {@link com.t24extractor.model.TafjVoc},
 *       {@link com.t24extractor.model.FileControl},
 *       {@link com.t24extractor.model.StandardSelectionRecord},
 *       {@link com.t24extractor.model.CobStatus} : dictionnaires du core
 *       banking, lus en seule lecture ;</li>
 *   <li><b>Entités BI (SQL Server)</b> — {@link com.t24extractor.model.SchemaSource},
 *       {@link com.t24extractor.model.SchemaSourceAudit},
 *       {@link com.t24extractor.model.MetaDataTable},
 *       {@link com.t24extractor.model.ScheduledOps} : schéma des colonnes
 *       extraites et opérations différées ;</li>
 *   <li><b>DTO de l'API REST</b> — les {@code …Request} et {@code …Result},
 *       qui fixent le contrat consommé par le frontend.</li>
 * </ul>
 *
 * <p>Les noms de colonnes sont portés par {@code @Column} car le nommage T24
 * (majuscules, points) ne suit aucune convention Java.
 */
package com.t24extractor.model;
