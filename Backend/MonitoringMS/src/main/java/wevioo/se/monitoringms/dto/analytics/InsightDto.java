package wevioo.se.monitoringms.dto.analytics;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Enseignement calculé automatiquement à partir des données, affiché dans le
 * bandeau « insight » des pages analytiques.
 *
 * <p>La localisation est déléguée au client : le backend n'envoie pas de texte
 * traduit mais une clé d'internationalisation et ses paramètres
 * d'interpolation. Le champ {@link #detail} n'est utilisé que pour les
 * compléments qui ne se traduisent pas, comme un code devise.</p>
 *
 * <p>Un enseignement n'est produit que si les données le rendent calculable ;
 * la liste renvoyée peut donc être plus courte que prévu, voire vide.</p>
 *
 * @see wevioo.se.monitoringms.service.AnalyticsService#insights(int)
 */
@Data
@Builder
public class InsightDto {

    /**
     * Nature de l'enseignement, qui détermine sa mise en forme :
     * {@code LEADER}, {@code CONCENTRATION} ou {@code PIC_ACTIVITE}.
     */
    private String type;

    /** Clé d'internationalisation du titre. */
    private String titleKey;

    /** Valeur principale mise en avant, déjà formatée pour l'affichage. */
    private String value;

    /** Complément brut, non traduit ; utilisé à défaut de {@link #detailKey}. */
    private String detail;

    /** Clé d'internationalisation du complément, interpolable via {@link #params}. */
    private String detailKey;

    /** Paramètres d'interpolation du complément, indexés par nom de variable. */
    private Map<String, Object> params;
}
