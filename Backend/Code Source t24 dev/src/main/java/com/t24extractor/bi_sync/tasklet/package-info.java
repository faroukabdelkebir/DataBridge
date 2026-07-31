/**
 * Synchronisation du schéma T24 vers la table BI (étape {@code BI_SYNC}).
 *
 * <p>Avant toute écriture de données, cette étape garantit que la table cible
 * existe et possède exactement les colonnes qui seront insérées. Elle applique
 * aussi les changements demandés depuis l'IHM (activation ou désactivation
 * d'une colonne), matérialisés dans la table d'audit SCHEMA_SOURCE_AUDIT.
 *
 * <p>Elle produit enfin les artefacts consommés par le reste du job :
 * dictionnaire nettoyé, liste des colonnes et requête d'INSERT — tous dérivés
 * du même ensemble, afin qu'ils ne puissent pas diverger.
 */
package com.t24extractor.bi_sync.tasklet;
