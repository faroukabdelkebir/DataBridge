package wevioo.se.authms.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration du client HTTP réactif utilisé par la couche proxy.
 *
 * @see wevioo.se.authms.proxy.ProxyService
 */
@Configuration
public class OAuth2ClientConfig {

    /** Constructeur par défaut, utilisé par Spring pour instancier cette classe de configuration. */
    public OAuth2ClientConfig() {
    }

    /**
     * Crée le {@link WebClient} employé pour relayer les requêtes vers les services en aval.
     *
     * <p>Aucune URL de base n'est fixée : le proxy construit l'URL cible complète à chaque appel.</p>
     *
     * @return un {@link WebClient} sans configuration spécifique
     */
    @Bean
    public WebClient proxyWebClient() {
        return WebClient.builder().build();
    }
}
