/**
 * Découpage d'une table en partitions traitées en parallèle.
 *
 * <p>Deux stratégies selon le mode de lecture :
 * <ul>
 *   <li>{@link com.t24extractor.partitioner.MssqlPartitioner} — attribue un
 *       simple index à chaque worker ; la sélection des lignes est déléguée à
 *       Oracle via une clause de hachage sur le ROWID ;</li>
 *   <li>{@link com.t24extractor.partitioner.ColumnRangePartitioner} — découpe
 *       en plages de numéros de ligne, pour la lecture au travers d'une vue.</li>
 * </ul>
 */
package com.t24extractor.partitioner;
