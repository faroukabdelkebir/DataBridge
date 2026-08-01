/**
 * Dépôts Spring Data JDBC sur la base <b>BI</b> (SQL Server).
 *
 * <p>Ils sont activés par
 * {@link com.t24extractor.configuration.BIDataSourceConfiguration}, qui les
 * relie au gestionnaire de transactions {@code biTransactionManager} et au
 * dialecte SQL Server. Les requêtes non dérivables du nom de méthode sont
 * déclarées via {@code @Query}, avec le SQL centralisé dans
 * {@link com.t24extractor.commons.Constants}.
 */
package com.t24extractor.repository.bi_repos;
