package wevioo.se.monitoringms.model;

import lombok.Builder;
import lombok.Data;

/**
 * Paramétrage d'une extraction T24, mappé depuis {@code BATCH_CONFIG}.
 *
 * <p>Une ligne par table extraite : elle décrit la sélection T24 à exécuter,
 * le mode de chargement retenu et la borne temporelle atteinte lors du dernier
 * passage. C'est le seul modèle du projet exposé en écriture, via l'activation
 * ou la désactivation d'une extraction.</p>
 *
 * @see wevioo.se.monitoringms.repository.BatchConfigRepository
 */
@Data
@Builder
public class BatchConfigRecord {

    /** Identifiant de la configuration, généralement le nom de la table. */
    private String  id;

    /** Table SQL Server alimentée par cette extraction. */
    private String  tableName;

    /** Nom de la sélection T24 exécutée pour produire le lot de données. */
    private String  t24SelectionName;

    /** Colonne servant de référence pour le découpage incrémental. */
    private String  colonneRef;

    /** Mode de chargement : complet ou incrémental. */
    private String  chargement;

    /** Borne haute atteinte lors du dernier chargement, au format {@code AAAAMMJJ}. */
    private Long    lastDate;

    /** Borne basse de l'historique à charger, au format {@code AAAAMMJJ}. */
    private Long    minDate;

    /** Indique si l'extraction est active ; une extraction désactivée est ignorée. */
    private boolean enabled;

    /** Indique si l'extraction est déclenchée automatiquement par le planificateur. */
    private boolean scheduler;

    /** Heure de déclenchement planifié, au format {@code HH:mm}. */
    private String  schedulerTime;
}
