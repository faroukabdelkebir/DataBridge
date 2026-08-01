package com.t24extractor.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import static com.t24extractor.commons.Constants.SCHEMA_SOURCE;


/**
 * Description d'une colonne extraite : son type ODS et son état d'activation.
 *
 * <p>SCHEMA_SOURCE est le référentiel qui décide, colonne par colonne, de ce
 * qui est extrait. Une colonne désactivée reste déclarée mais n'entre ni dans
 * la table BI, ni dans l'INSERT.
 *
 * @see com.t24extractor.bi_sync.tasklet.BISyncTasklet
 * @see SchemaSourceAudit
 */
@Table(value = SCHEMA_SOURCE)
@Data
@AllArgsConstructor
public class SchemaSource implements Persistable<String> {

    @Id
    String id;

    @Column("table_name")
    String tableName;

    @Column("column_name")
    String columnName;

    @Column("datatype")
    String dataType;

    @Column("_enabled")
    boolean enabled;

    @Column("indexed")
    boolean indexed;

    @Column("ssTag")
    String ssTag;

    @Column("single_mult")
    String singleMult;

    @Column("local_ref")
    boolean localRef;

    @Override
    public String getId() {
        return id;
    }

    /**
     * Toujours {@code true} : la table est alimentée en ajout, ce qui force
     * Spring Data JDBC à émettre un INSERT plutôt qu'un UPDATE.
     *
     * @return {@code true}
     */
    @Override
    public boolean isNew() {
        return true;
    }

}
