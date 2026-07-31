/**
 * Liaison des documents JSON aux paramètres de l'INSERT.
 *
 * <p>{@link com.t24extractor.preparedstatementsetter.ItemPrepStmtSetter}
 * positionne un paramètre par colonne, dans l'ordre exact de la liste
 * {@code COLUMNS} produite à l'étape BI_SYNC — le même ordre que celui des
 * points d'interrogation de {@code SQL_QUERY}.
 */
package com.t24extractor.preparedstatementsetter;
