package wevioo.se.monitoringms.model;

import lombok.Builder;
import lombok.Data;

/**
 * Événement d'audit du schéma T24, mappé depuis {@code SCHEMA_SOURCE_AUDIT}.
 *
 * <p>Chaque ligne conserve l'ancienne et la nouvelle valeur des indicateurs
 * d'une colonne du dictionnaire. La table est alimentée automatiquement par
 * le déclencheur SQL Server {@code schemaSourceTriggerAudit} ; l'application
 * ne fait que la lire pour détecter les dérives de schéma.</p>
 *
 * <p>Les indicateurs sont stockés en entier ({@code 0} / {@code 1}) et non en
 * booléen, conformément au type de la colonne source.</p>
 *
 * @see SchemaSourceRecord colonne du dictionnaire concernée par l'événement
 */
@Data
@Builder
public class SchemaAuditRecord {

    /** Clé primaire auto-incrémentée de l'événement d'audit. */
    private int     id;

    /** Identifiant de la colonne modifiée dans {@code SCHEMA_SOURCE}. */
    private String  sourceId;

    /** Table T24 à laquelle appartient la colonne modifiée. */
    private String  sourceTableName;

    /** Nom de la colonne modifiée. */
    private String  sourceColumnName;

    /** Type de données de la colonne au moment de la modification. */
    private String  sourceDataType;

    /** Indicateur d'extraction après modification ({@code 0} ou {@code 1}). */
    private int     newSourceEnabled;

    /** Indicateur d'extraction avant modification ({@code 0} ou {@code 1}). */
    private int     oldSourceEnabled;

    /** Indicateur d'indexation après modification ({@code 0} ou {@code 1}). */
    private int     newSourceIndexed;

    /** Indicateur d'indexation avant modification ({@code 0} ou {@code 1}). */
    private int     oldSourceIndexed;

    /** Compte ayant réalisé la modification, tel que vu par le déclencheur. */
    private String  updatedBy;

    /** Horodatage de la modification. */
    private Long    updatedOn;

    /**
     * Indique si l'événement correspond à l'activation de l'extraction.
     *
     * @return {@code true} si l'indicateur est passé de {@code 0} à {@code 1}
     */
    public boolean wasActivated()   { return oldSourceEnabled == 0 && newSourceEnabled == 1; }

    /**
     * Indique si l'événement correspond à la désactivation de l'extraction.
     *
     * @return {@code true} si l'indicateur est passé de {@code 1} à {@code 0}
     */
    public boolean wasDeactivated() { return oldSourceEnabled == 1 && newSourceEnabled == 0; }

    /**
     * Indique si l'événement correspond à la pose d'un index sur la colonne.
     *
     * @return {@code true} si l'indicateur d'indexation est passé de {@code 0} à {@code 1}
     */
    public boolean wasIndexed()     { return oldSourceIndexed == 0 && newSourceIndexed == 1; }
}
