package com.t24extractor.preparedstatementsetter;

import com.alibaba.fastjson2.JSONObject;
import com.google.gson.Gson;
import org.springframework.batch.infrastructure.item.database.ItemPreparedStatementSetter;
import org.springframework.beans.factory.annotation.Value;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Alimente les paramètres de l'INSERT à partir d'un document JSON.
 *
 * <p>Les paramètres sont positionnés dans l'ordre de la liste {@code COLUMNS},
 * qui est celui des points d'interrogation de {@code SQL_QUERY} — les deux
 * étant produits ensemble par l'étape BI_SYNC. Un écart entre les deux
 * décalerait silencieusement les valeurs insérées.
 *
 * <p>Les champs multivalués, portés par des listes, sont sérialisés en JSON :
 * ils seront éclatés en tables satellites par la normalisation. Un champ absent
 * du document devient {@code NULL}.
 */
public class ItemPrepStmtSetter implements ItemPreparedStatementSetter<JSONObject> {

    @Value("#{jobExecutionContext['COLUMNS']}")
    List<String> columns;

    Gson gson = new Gson();

    /**
     * {@inheritDoc}
     *
     * @param document          document produit par le processeur
     * @param preparedStatement requête d'insertion à paramétrer
     * @throws SQLException si un paramètre ne peut pas être positionné
     */
    @Override
    public void setValues(JSONObject document, PreparedStatement preparedStatement) throws SQLException {

        int i = 1;
        for (String key : columns) {
            if (document.get(key) != null) {
                if (document.get(key) instanceof List) {
                    preparedStatement.setString(i, gson.toJson(document.get(key)));
                } else {
                    preparedStatement.setObject(i, document.getOrDefault(key, Object.class));
                }
            } else {
                preparedStatement.setString(i, null);
            }
            i++;
        }

    }
}
