/**
 * Étapes séquentielles du pipeline d'extraction.
 *
 * <p>Chaque {@code Tasklet} prépare le terrain pour les suivantes en écrivant
 * dans le {@code JobExecutionContext}. Les clés échangées forment un contrat
 * implicite mais strict :
 *
 * <table class="striped">
 *   <caption>Clés du contexte d'exécution</caption>
 *   <thead>
 *     <tr><th>Clé</th><th>Écrite par</th><th>Lue par</th></tr>
 *   </thead>
 *   <tbody>
 *     <tr><td>{@code Table_Name}</td><td>TableNameTasklet</td><td>lecture Oracle</td></tr>
 *     <tr><td>{@code Type}</td><td>TableNameTasklet</td><td>QueryTasklet, ExecutionFlowDecider</td></tr>
 *     <tr><td>{@code sysFieldOds}</td><td>StandardSelectionTasklet</td><td>BISyncTasklet, Json_Flattener</td></tr>
 *     <tr><td>{@code VIEW_QUERY}</td><td>StandardSelectionTasklet (BLOB)</td><td>lecteur BLOB</td></tr>
 *     <tr><td>{@code QUERY}</td><td>QueryTasklet</td><td>lecteur XML</td></tr>
 *     <tr><td>{@code COLUMNS}, {@code SQL_QUERY}</td><td>BISyncTasklet</td><td>writer JDBC</td></tr>
 *   </tbody>
 * </table>
 *
 * <p>Ce package héberge aussi {@link com.t24extractor.tasklet.FileControlServiceImpl},
 * service de classification des tables T24 utilisé par les tasklets.
 */
package com.t24extractor.tasklet;
