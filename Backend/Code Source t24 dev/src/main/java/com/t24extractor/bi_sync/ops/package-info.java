/**
 * Génération du SQL propre à chaque SGBD.
 *
 * <p>{@link com.t24extractor.bi_sync.ops.SqlOps} définit le contrat (créer une
 * table cible, ajouter ou supprimer une colonne, construire un INSERT, gérer
 * les index, compter les lignes) et regroupe ce qui est indépendant du dialecte.
 *
 * <p>Deux implémentations, aux responsabilités volontairement asymétriques :
 * <ul>
 *   <li>{@link com.t24extractor.bi_sync.ops.SqlServerOps} — la cible BI, seule à
 *       écrire : DDL, INSERT, MERGE, index, normalisation JSON ;</li>
 *   <li>{@link com.t24extractor.bi_sync.ops.OracleOps} — la source T24, en
 *       lecture seule : seuls les comptages sont supportés, toute opération
 *       d'écriture lève {@code UnsupportedOperationException}.</li>
 * </ul>
 *
 * <p>Le SQL produit est un livrable à part entière : l'échappement, l'ordre des
 * colonnes et le nombre de paramètres sont figés par des tests de
 * non-régression.
 */
package com.t24extractor.bi_sync.ops;
