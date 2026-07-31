package wevioo.se.authms.proxy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import java.nio.charset.StandardCharsets;

import java.net.URI;

/**
 * Relaie les requêtes du « Backend For Frontend » vers les services internes.
 *
 * <p>Réémet la requête entrante vers le service cible en y substituant un jeton machine
 * obtenu via le grant {@code client_credentials} : le jeton de l'utilisateur final n'est
 * jamais transmis en aval.</p>
 *
 * @see ProxyController
 */
@Service
public class ProxyService {

    /** Client HTTP réactif employé pour émettre les requêtes en aval. */
    private final WebClient webClient;

    /** Gestionnaire fournissant le jeton d'accès machine du client {@code frontend-bff}. */
    private final OAuth2AuthorizedClientManager clientManager;

    /**
     * Construit le service avec le client HTTP et le gestionnaire d'autorisations.
     *
     * @param proxyWebClient client HTTP dédié au relais
     * @param clientManager  gestionnaire de clients OAuth2 autorisés
     */
    public ProxyService(WebClient proxyWebClient, OAuth2AuthorizedClientManager clientManager) {
        this.webClient = proxyWebClient;
        this.clientManager = clientManager;
    }

    /**
     * Rejoue la requête entrante vers targetBase + targetPath avec le token
     * machine 'frontend-bff'. Statut, corps et content-type du service cible
     * sont retransmis tels quels (y compris les erreurs 4xx/5xx).
     *
     * <p>Les autres en-têtes de la réponse amont sont délibérément écartés : recopier
     * {@code Transfer-Encoding} ou {@code Content-Length} entrerait en conflit avec l'encodage
     * calculé par le conteneur servlet. Un service cible injoignable est converti en
     * {@code 503} accompagné d'un corps JSON explicite, plutôt qu'en {@code 500} opaque.</p>
     *
     * @param request    requête entrante, dont la méthode, la chaîne de requête et le type de contenu sont repris
     * @param targetBase URL de base du service cible
     * @param targetPath chemin à appeler sur le service cible
     * @param body       corps de la requête, {@code null} ou vide si la méthode n'en porte pas
     * @return la réponse du service cible, ou une réponse {@code 503} s'il est injoignable
     * @throws IllegalStateException si le jeton machine ne peut pas être obtenu
     */
    public ResponseEntity<byte[]> forward(HttpServletRequest request,
                                          String targetBase,
                                          String targetPath,
                                          byte[] body) {
        String query = request.getQueryString();
        URI uri = URI.create(targetBase + targetPath + (query != null ? "?" + query : ""));

        try {
            WebClient.RequestBodySpec spec = webClient
                    .method(HttpMethod.valueOf(request.getMethod()))
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + machineToken());

            if (request.getContentType() != null) {
                spec = (WebClient.RequestBodySpec) spec.header(HttpHeaders.CONTENT_TYPE, request.getContentType());
            }

            ResponseEntity<byte[]> upstream =
                    (body != null && body.length > 0 ? spec.bodyValue(body) : spec)
                            .exchangeToMono(resp -> resp.toEntity(byte[].class))
                            .block();

            // NE PAS recopier les headers amont (Transfer-Encoding, Content-Length...)
            var out = ResponseEntity.status(upstream.getStatusCode());
            MediaType ct = upstream.getHeaders().getContentType();
            if (ct != null) out.contentType(ct);
            return out.body(upstream.getBody());

        } catch (WebClientRequestException e) {
            // Service cible éteint / injoignable → 503 propre au lieu d'un 500 opaque
            String msg = "{\"message\":\"Service cible injoignable : " + uri.getHost() + ":" + uri.getPort() + "\"}";
            return ResponseEntity.status(503)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(msg.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Obtient le jeton d'accès machine du client {@code frontend-bff}.
     *
     * <p>Le jeton est mis en cache et renouvelé automatiquement par le gestionnaire de clients
     * autorisés ; chaque appel ne déclenche donc pas nécessairement une requête au serveur
     * d'autorisation.</p>
     *
     * @return la valeur du jeton d'accès à placer dans l'en-tête {@code Authorization}
     * @throws IllegalStateException si l'autorisation échoue et qu'aucun jeton n'est disponible
     */
    private String machineToken() {
        OAuth2AuthorizedClient client = clientManager.authorize(
                OAuth2AuthorizeRequest.withClientRegistrationId("frontend-bff")
                        .principal("bff-internal")
                        .build());
        if (client == null) {
            throw new IllegalStateException("Impossible d'obtenir le token machine frontend-bff");
        }
        return client.getAccessToken().getTokenValue();
    }
}