package wevioo.se.authms.Config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ClientCredentialsOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * Configuration de l'infrastructure technique du microservice.
 *
 * <p>Active les dépôts JPA et déclare les beans nécessaires aux appels sortants
 * vers UserMS, sécurisés par le flow OAuth2 {@code client_credentials}.</p>
 */
@Configuration
@EnableJpaRepositories(basePackages = "wevioo.se.authms.Repositories")
public class InfraConfig {

    /** URL de base du microservice utilisateurs (UserMS). */
    @Value("${app.user-service.url:http://localhost:8085}")
    private String userServiceUrl;

    /** Constructeur par défaut, utilisé par Spring pour instancier cette classe de configuration. */
    public InfraConfig() {
    }

    /**
     * Gestionnaire de clients OAuth2 pour les appels service-à-service
     * (hors contexte de requête HTTP entrante). Il obtient un access token
     * via le grant client_credentials, le met en cache, et le renouvelle
     * automatiquement à l'expiration.
     *
     * @param clientRegistrationRepository registre des clients OAuth2 déclarés en configuration
     * @param authorizedClientService      service de persistance des clients autorisés
     * @return le gestionnaire de clients autorisés utilisé par les intercepteurs OAuth2
     */
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {

        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientService);
        manager.setAuthorizedClientProvider(new ClientCredentialsOAuth2AuthorizedClientProvider());
        return manager;
    }

    /**
     * RestClient vers UserMS. L'intercepteur OAuth2 attache automatiquement
     * l'en-tête {@code Authorization: Bearer <jwt>} obtenu via le flow
     * client_credentials (registration "userms" dans application.properties).
     *
     * @param authorizedClientManager gestionnaire fournissant le jeton d'accès service-à-service
     * @return un {@link RestClient} préconfiguré (URL de base, en-têtes JSON, authentification)
     */
    @Bean
    public RestClient restClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        OAuth2ClientHttpRequestInterceptor oauth2Interceptor =
                new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
        oauth2Interceptor.setClientRegistrationIdResolver(request -> "userms");

        return RestClient.builder()
                .baseUrl(userServiceUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept",       MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor(oauth2Interceptor)
                .build();
    }
}

