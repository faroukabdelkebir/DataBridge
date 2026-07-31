package com.t24extractor.tasklet;

import com.t24extractor.bi_sync.ops.SqlOps;
import com.t24extractor.bi_sync.ops.OracleOps;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.t24extractor.commons.Constants.PARTITION_BY_ROWID;
import static com.t24extractor.commons.Constants.SELECT_XML_RECORDS_TPL;
import static com.t24extractor.commons.Constants.WHERE_RECID_CURRENT_AND_PREVIOUS_MONTH;


/**
 * Construit la requête de lecture Oracle selon le mode de chargement.
 *
 * <h2>Modes de chargement</h2>
 * <ul>
 *   <li>{@code Full} — relit toute la table ;</li>
 *   <li>{@code Incremental} — ne reprend que les enregistrements postérieurs à
 *       la dernière extraction, via une colonne de date de référence ;</li>
 *   <li>{@code Incremental-specific} — filtre sur le mois courant et le
 *       précédent, à partir du motif de RECID ;</li>
 *   <li>{@code Incremental-id} — l'écrémage se fait en aval, sur les
 *       identifiants déjà extraits ; aucune borne n'est posée ici.</li>
 * </ul>
 *
 * <p>La date sentinelle {@code 00000000} marque une première extraction : aucune
 * borne n'est alors appliquée.
 *
 * <p>Au-delà de 1 000 lignes, une clause de partitionnement par hachage du
 * ROWID est ajoutée pour que chaque worker ne lise que sa part.
 *
 * <p>La requête produite est publiée sous la clé {@code QUERY}, et seulement
 * pour les tables XML : les tables BLOB sont lues au travers de la vue
 * construite à l'étape STANDARD.SELECTION.
 */
@Slf4j
public class QueryTasklet implements Tasklet {
    /** Mot-clé introduisant le premier filtre de la requête. */
    public static final String WHERE_CLAUSE = "WHERE";

    /** Mot-clé enchaînant un filtre supplémentaire sur un WHERE existant. */
    public static final String AND_CLAUSE = "AND";
    private static final CharSequence SPACE = " ";

    private final JdbcTemplate jdbcTemplate;

    private final SqlOps sqlOps = new OracleOps();

    public QueryTasklet(@Qualifier("jdbcTemplateT24") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Setter
    private String tableName;

    @Setter
    private String chargement;
    @Setter
    private String lastDate;
    @Setter
    private String columnRef;
    @Setter
    private String futureDate;
    @Setter
    private String dateFormat;

    /**
     * {@inheritDoc}
     *
     * <p>N'écrit {@code QUERY} que si le type de stockage vaut {@code X} : pour
     * une table BLOB, la requête de lecture est la vue et non un SELECT direct.
     *
     * @return toujours {@link RepeatStatus#FINISHED}
     */
    @Override
    public RepeatStatus execute(StepContribution stepContribution, @NonNull ChunkContext chunkContext) throws Exception {
        String type = String.valueOf(stepContribution.getStepExecution().getJobExecution().getExecutionContext().get("Type", Character.class));

        String dataQuery = getDataQuery();

        if ("X".equalsIgnoreCase(type)) {
            chunkContext.getStepContext()
                    .getStepExecution()
                    .getJobExecution()
                    .getExecutionContext()
                    .put("QUERY", dataQuery);
        }

        return RepeatStatus.FINISHED;
    }

    private String getDataQuery() {
        String dataQuery = String.format(SELECT_XML_RECORDS_TPL, this.tableName);
        dataQuery = String.join(SPACE, dataQuery, getQueryCondition());


        StringBuilder partitioningQuery = new StringBuilder(PARTITION_BY_ROWID);

        if (getQueryCondition().contains(WHERE_CLAUSE))
            partitioningQuery.replace(0, WHERE_CLAUSE.length(), AND_CLAUSE);

        if (sqlOps.countAll(jdbcTemplate, tableName) >= 1_000)
            dataQuery = String.join(SPACE, dataQuery, partitioningQuery);


        return dataQuery;

    }

    private String getQueryCondition() {
        switch (chargement) {
            case "Full", "Incremental-id" -> {
                return "";
            }
            case "Incremental" -> {
                return incrementalCondition();
            }
            case "Incremental-specific" -> {
                return incrementalSpecificCondition();
            }
            default -> {
                return "";
            }
        }
    }

    private String incrementalCondition() {
        if (isBlank(columnRef)) {
            return "";
        }
        if (isBlank(futureDate)) {
            if ("00000000".equals(lastDate)) {
                return "";
            }
            return isVarcharDate()
                    ? WHERE_CLAUSE + " " + columnRef + " > " + "'" + lastDate + "'"
                    : WHERE_CLAUSE + " " + columnRef + " > " + lastDate;
        }
        return isVarcharDate()
                ? WHERE_CLAUSE + " " + columnRef + " > '" + lastDate + "'" + " AND " + columnRef + " <= '" + futureDate + "'"
                : WHERE_CLAUSE + " " + columnRef + " > " + lastDate + " AND " + columnRef + " <= " + futureDate;
    }

    private String incrementalSpecificCondition() {
        if ("000000".equals(lastDate)) {
            return "";
        }
        return WHERE_RECID_CURRENT_AND_PREVIOUS_MONTH;
    }

    private boolean isVarcharDate() {
        return isBlank(dateFormat) || "varchar".equalsIgnoreCase(dateFormat);
    }

    private boolean isBlank(String value) {
        return value == null || value.isEmpty();
    }
}
