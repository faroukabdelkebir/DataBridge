/**
 * Extracteur T24 — alimentation du socle décisionnel de la Banque.
 *
 * <h2>Rôle</h2>
 * L'application lit les tables du core banking <b>Temenos T24</b> (Oracle,
 * enregistrements stockés en XML ou en BLOB) et les restitue sous forme
 * relationnelle exploitable dans l'entrepôt <b>BI</b> (SQL Server).
 *
 * <h2>Deux bases, deux rôles</h2>
 * <ul>
 *   <li><b>Oracle / T24</b> — source, accédée en lecture seule ;</li>
 *   <li><b>SQL Server / BI</b> — cible, qui héberge aussi les métadonnées
 *       Spring Batch et la configuration des extractions (BATCH_CONFIG).</li>
 * </ul>
 * Chaque base a sa propre pile Spring Data JDBC : voir
 * {@link com.t24extractor.configuration.T24DataSourceConfiguration} et
 * {@link com.t24extractor.configuration.BIDataSourceConfiguration}.
 *
 * <h2>Déroulé d'une extraction</h2>
 * Un job traite une table T24 en enchaînant les étapes suivantes, qui se
 * transmettent leurs résultats par le {@code JobExecutionContext} :
 * <ol>
 *   <li>{@code COB_STATUS} — n'extrait que si la clôture journalière T24 est
 *       arrêtée ({@link com.t24extractor.tasklet.CobStatusTasklet}) ;</li>
 *   <li>{@code TableName} — résout le nom Oracle et le mode de stockage
 *       ({@link com.t24extractor.tasklet.TableNameTasklet}) ;</li>
 *   <li>{@code Standard.Selection} — lit le dictionnaire de champs T24
 *       ({@link com.t24extractor.tasklet.StandardSelectionTasklet}) ;</li>
 *   <li>{@code QueryBuildingStep} — construit la requête de lecture Oracle
 *       ({@link com.t24extractor.tasklet.QueryTasklet}) ;</li>
 *   <li>{@code BI_SYNC} — aligne la table BI sur le schéma attendu
 *       ({@link com.t24extractor.bi_sync.tasklet.BISyncTasklet}) ;</li>
 *   <li>{@code Manager} — lecture partitionnée, transformation et écriture ;</li>
 *   <li>{@code JSON_NORMALIZATION} — éclate les champs multivalués en tables
 *       satellites ({@link com.t24extractor.tasklet.Json_Flattener}).</li>
 * </ol>
 *
 * <h2>Déclenchement</h2>
 * Les extractions partent soit d'un appel REST
 * ({@link com.t24extractor.controller.JobLauncherController}), soit de la
 * planification horaire
 * ({@link com.t24extractor.scheduler.AutoExtractionScheduler}).
 *
 * @see com.t24extractor.configuration.BatchConfiguration
 */
package com.t24extractor;
