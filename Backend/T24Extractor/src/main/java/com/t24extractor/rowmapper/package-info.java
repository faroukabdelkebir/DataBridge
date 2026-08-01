/**
 * Conversion des lignes JDBC lues sur Oracle en objets du domaine.
 *
 * <p>Deux mappers, un par mode de stockage T24 :
 * {@link com.t24extractor.rowmapper.XMLRowMapper} pour les tables dont
 * l'enregistrement tient dans un XMLRECORD, et
 * {@link com.t24extractor.rowmapper.BlobRowMapper} pour celles lues au travers
 * d'une vue éclatée en colonnes.
 */
package com.t24extractor.rowmapper;
