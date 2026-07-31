package wevioo.se.monitoringms.service;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Traduit en français les valeurs techniques destinées à l'affichage.
 *
 * <p>La traduction ne s'applique qu'à la présentation : les noms de classes,
 * d'attributs et les valeurs stockées en base restent inchangés, ce qui
 * préserve la comparabilité avec les statuts Spring Batch d'origine.</p>
 */
@Service
public class DisplayTranslationService {

    /** Correspondance entre statut Spring Batch et libellé français. */
    private static final Map<String, String> STATUSES = Map.ofEntries(
            Map.entry("ABANDONED", "Abandonné"),
            Map.entry("COMPLETED", "Terminé"),
            Map.entry("EXECUTING", "En cours d'exécution"),
            Map.entry("FAILED", "Échec"),
            Map.entry("NOOP", "Aucune action"),
            Map.entry("STARTED", "En cours"),
            Map.entry("STARTING", "Démarrage"),
            Map.entry("STOPPED", "Arrêté"),
            Map.entry("STOPPING", "Arrêt en cours"),
            Map.entry("UNKNOWN", "Inconnu")
    );

    /**
     * Traduit un statut d'exécution en libellé français.
     *
     * <p>La comparaison ignore la casse. Un statut inconnu est renvoyé tel
     * quel plutôt que remplacé : mieux vaut afficher une valeur technique
     * qu'en masquer une nouvelle.</p>
     *
     * @param value statut technique à traduire, éventuellement {@code null}
     * @return le libellé français, ou la valeur d'entrée si elle est nulle,
     *         vide ou non reconnue
     */
    public String translateStatus(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return STATUSES.getOrDefault(value.toUpperCase(), value);
    }
}
