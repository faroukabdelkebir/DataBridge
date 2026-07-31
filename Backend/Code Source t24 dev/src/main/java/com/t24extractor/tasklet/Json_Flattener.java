package com.t24extractor.tasklet;

import lombok.Setter;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.t24extractor.commons.Constants.*;

/**
 * Normalise les champs multivalués (JSON) de la table principale vers des
 * tables satellites « TABLE#CHAMP ».
 *
 * v2 — IDEMPOTENT : chaque satellite est vidé avant d'être realimenté.
 * L'ancienne version réinsérait l'intégralité des lignes à chaque exécution
 * du job, ce qui multipliait les volumes (ex. 4 000 factures → 16 000 lignes
 * dans AA_BILL_DETAILS#SETTLE_STATUS après 4 runs) et faussait toutes les
 * agrégations en aval.
 */
@Component
@StepScope
public class Json_Flattener implements Tasklet {

    private final JdbcTemplate jdbcTemplate;

    @Setter
    protected String table;

    public Json_Flattener(@Qualifier("jdbcBI") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    /**
     * {@inheritDoc}
     *
     * <p>Pour chaque champ multivalué de la table, crée si besoin la table
     * satellite {@code TABLE#CHAMP}, la vide, puis la réalimente en éclatant le
     * JSON avec {@code OPENJSON}. Les colonnes absentes de la table principale
     * et le champ {@code DATE_TIME} sont ignorés.
     *
     * @return toujours {@link RepeatStatus#FINISHED}
     */
    @Override
    public RepeatStatus execute(StepContribution stepContribution, @NonNull ChunkContext chunkContext) throws Exception {

        JSONObject nameJs = new JSONObject(
                stepContribution.getStepExecution()
                        .getJobExecution()
                        .getExecutionContext()
                        .getString(BI_STANDARD_SELECTION)
        );
        String name = this.table;

        Iterator<String> iterator = nameJs.keys();

        StringBuilder sb = new StringBuilder();
        List<String> deleteQueries = new ArrayList<>();
        List<String> insertQueries = new ArrayList<>();
        while (iterator.hasNext()) {
            String key = iterator.next();

            if (!columnExistsInMainTable(name, key)) {
                continue;
            }

            if (!key.contains("Local_Ref") && !key.equals("DATE_TIME")
                    && nameJs.get(key).toString().contains("MAX")) {

                sb.append(String.format(CREATE_SATELLITE_TABLE_TPL, name, key, ID_TYPE));

                // Idempotence : purge avant réalimentation complète
                deleteQueries.add(purgeQuery(name, key));

                insertQueries.add(String.format(INSERT_SATELLITE_TPL, name, key));

            } else if (key.contains("Local_Ref") && key.contains("MAX")) {

                sb.append(String.format(CREATE_LOCAL_REF_SATELLITE_TPL, name, key, ID_TYPE));

                // Idempotence : purge avant réalimentation complète
                deleteQueries.add(purgeQuery(name, key));

                insertQueries.add(String.format(INSERT_LOCAL_REF_SATELLITE_TPL, name, key));
            }
        }

        if (!sb.isEmpty()) {
            jdbcTemplate.execute(sb.toString());
        }

        if (!deleteQueries.isEmpty()) {
            jdbcTemplate.batchUpdate(deleteQueries.toArray(new String[0]));
        }

        if (!insertQueries.isEmpty()) {
            jdbcTemplate.batchUpdate(insertQueries.toArray(new String[0]));
        }

        return RepeatStatus.FINISHED;
    }

    /** Vide un satellite s'il existe — rend la normalisation rejouable. */
    private String purgeQuery(String tableName, String key) {
        return String.format(PURGE_SATELLITE_TPL, tableName, key);
    }

    private boolean columnExistsInMainTable(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                COUNT_COLUMN_IN_TABLE, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }
}
