package wevioo.se.monitoringms.model;

import lombok.Builder;
import lombok.Data;

/**
 * Colonne du dictionnaire de schéma T24, mappée depuis {@code SCHEMA_SOURCE}.
 *
 * <p>Décrit la correspondance entre un champ T24 et sa colonne SQL Server,
 * ainsi que les options de chargement retenues pour ce champ. Le contenu de
 * cette table pilote l'extraction : une colonne non activée n'est pas
 * alimentée, ce qui explique que certains indicateurs analytiques restent
 * indisponibles tant que l'extraction n'a pas été enrichie.</p>
 *
 * @see SchemaAuditRecord historique des modifications de ce dictionnaire
 */
@Data
@Builder
public class SchemaSourceRecord {

    /** Identifiant du mapping, de la forme {@code TABLE.COLONNE}. */
    private String  id;

    /** Table T24 concernée : {@code CUSTOMER}, {@code FUNDS_TRANSFER#HIS}… */
    private String  tableName;

    /** Nom de la colonne dans la table cible SQL Server. */
    private String  columnName;

    /** Type de données SQL Server retenu pour la colonne. */
    private String  dataType;

    /** Indique si la colonne est effectivement extraite. */
    private boolean enabled;

    /** Indique si un index a été posé sur la colonne côté SQL Server. */
    private boolean indexed;

    /** Position du champ dans l'enregistrement T24 (« sous-tag » du dictionnaire). */
    private String  ssTag;

    /** Cardinalité du champ T24 : {@code S} pour monovalué, {@code M} pour multivalué. */
    private String  singleMult;

    /** Indique si le champ provient de la zone {@code LOCAL.REF} de T24. */
    private boolean localRef;

    /** Horodatage d'insertion de la ligne dans le dictionnaire. */
    private Long    insertDate;
}
