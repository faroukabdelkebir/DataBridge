package wevioo.se.monitoringms.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Alerte active exposée au tableau de bord.
 *
 * <p>Le message est transmis sous forme de clé d'internationalisation
 * accompagnée de ses paramètres d'interpolation, le front se chargeant du
 * rendu final. Les champs bruts {@code type} et {@code message} ne subsistent
 * que pour les clients antérieurs à l'internationalisation.</p>
 *
 * @see wevioo.se.monitoringms.service.AlertService
 */
@Data
@Builder
public class AlertDto {

    /**
     * Identifiant stable de l'alerte, par exemple {@code CONSECUTIVE_FAILURE_T24}.
     * Sert de clé de déduplication et d'acquittement.
     */
    private String alertKey;

    /**
     * Libellé brut du type d'alerte. Déprécié au profit de {@link #typeKey} ;
     * conservé pour les clients antérieurs à l'internationalisation.
     */
    private String        type;

    /** Clé d'internationalisation du type d'alerte. */
    private String        typeKey;

    /** Gravité : {@code CRITICAL}, {@code WARNING} ou {@code INFO}. */
    private String        severity;

    /** Traitement concerné ; {@code null} pour une alerte non rattachée à un traitement. */
    private String        jobName;

    /**
     * Message brut de l'alerte. Déprécié au profit de {@link #messageKey}
     * et {@link #params}.
     */
    private String        message;

    /** Clé d'internationalisation du message. */
    private String        messageKey;

    /** Paramètres d'interpolation du message, indexés par nom de variable. */
    private Map<String, Object> params;

    /** Date de première détection de la condition d'alerte. */
    private LocalDateTime triggeredAt;

    /** Indique si l'alerte a été acquittée. */
    private boolean       resolved;
}
