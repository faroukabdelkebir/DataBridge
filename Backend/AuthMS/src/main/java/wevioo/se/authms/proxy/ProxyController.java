package wevioo.se.authms.proxy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur « Backend For Frontend » exposé sous {@code /bff}.
 *
 * <p>Point d'entrée unique du front-end vers les services internes : chaque famille de
 * chemins est réécrite puis relayée vers le service correspondant. Le front n'a ainsi ni à
 * connaître les adresses internes ni à gérer l'authentification machine-à-machine, celle-ci
 * étant assurée par {@link ProxyService}.</p>
 *
 * <p>Table de routage :</p>
 * <ul>
 *   <li>{@code /bff/users/**} → UserMS, sous {@code /api/v1/users/**} ;</li>
 *   <li>{@code /bff/ai/**} → service d'agents IA, chemins conservés à l'identique ;</li>
 *   <li>{@code /bff/batch/**} → service d'extraction, sous {@code /api/v1/**}.</li>
 * </ul>
 *
 * @see ProxyService
 */
@RestController
@RequestMapping("/bff")
public class ProxyController {

    /** Service assurant le relais effectif des requêtes. */
    private final ProxyService proxy;

    /** URL de base du microservice utilisateurs. */
    @Value("${app.bff.targets.userms}")
    private String usermsUrl;

    /** URL de base du service d'agents IA. */
    @Value("${app.bff.targets.aiagents}")
    private String aiagentsUrl;

    /** URL de base du service d'extraction par lots. */
    @Value("${app.bff.targets.extractor}")
    private String extractorUrl;

    /**
     * Construit le contrôleur avec son service de relais.
     *
     * @param proxy service de relais des requêtes vers les services en aval
     */
    public ProxyController(ProxyService proxy) {
        this.proxy = proxy;
    }

    /**
     * Relaie les requêtes {@code /bff/users/**} vers UserMS sous {@code /api/v1/users/**}.
     *
     * <p>Accepte toutes les méthodes HTTP.</p>
     *
     * @param req  requête entrante, dont la méthode, la chaîne de requête et le type de contenu sont conservés
     * @param body corps de la requête, absent pour les méthodes qui n'en portent pas
     * @return la réponse du service cible, statut et corps retransmis tels quels
     */
    @RequestMapping("/users/**")
    public ResponseEntity<byte[]> users(HttpServletRequest req,
                                        @RequestBody(required = false) byte[] body) {
        return proxy.forward(req, usermsUrl, "/api/v1/users" + suffix(req, "/bff/users"), body);
    }

    /**
     * Relaie les requêtes {@code /bff/ai/**} vers le service d'agents IA.
     *
     * <p>Le chemin est transmis sans préfixe supplémentaire afin de respecter les routes
     * Django du service cible, slash final compris.</p>
     *
     * @param req  requête entrante
     * @param body corps de la requête, éventuellement absent
     * @return la réponse du service cible, statut et corps retransmis tels quels
     */
    @RequestMapping("/ai/**")
    public ResponseEntity<byte[]> ai(HttpServletRequest req,
                                     @RequestBody(required = false) byte[] body) {
        return proxy.forward(req, aiagentsUrl, suffix(req, "/bff/ai"), body);
    }

    /**
     * Relaie les requêtes {@code /bff/batch/**} vers le service d'extraction sous {@code /api/v1/**}.
     *
     * @param req  requête entrante
     * @param body corps de la requête, éventuellement absent
     * @return la réponse du service cible, statut et corps retransmis tels quels
     */
    @RequestMapping("/batch/**")
    public ResponseEntity<byte[]> batch(HttpServletRequest req,
                                        @RequestBody(required = false) byte[] body) {
        return proxy.forward(req, extractorUrl, "/api/v1" + suffix(req, "/bff/batch"), body);
    }

    /**
     * Extrait la portion de l'URI située après le préfixe de routage.
     *
     * <p>Par exemple, {@code /bff/users/42} avec le préfixe {@code /bff/users} donne
     * {@code /42}. Une requête portant exactement sur le préfixe donne une chaîne vide.</p>
     *
     * @param req    requête entrante
     * @param prefix préfixe de routage à retirer
     * @return le suffixe à concaténer à l'URL du service cible
     */
    private String suffix(HttpServletRequest req, String prefix) {
        String path = req.getRequestURI();
        return path.length() > prefix.length() ? path.substring(prefix.length()) : "";
    }
}
