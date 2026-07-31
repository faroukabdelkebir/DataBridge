package wevioo.se.authms.Controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.UUID;


/**
 * Contrôleur REST d'administration des clients OAuth2, exposé sous
 * {@code /api/v1/oauth/clients}.
 *
 * <p>Permet d'enregistrer et de consulter les clients machine-à-machine autorisés à obtenir
 * un jeton d'accès via le grant {@code client_credentials}. L'ensemble des points d'entrée
 * est réservé aux administrateurs.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/oauth/clients")
@PreAuthorize("hasRole('ADMIN')")
public class OAuthClientController {

    /** Dépôt de persistance des clients OAuth2 enregistrés. */
    private final RegisteredClientRepository clientRepo;

    /** Encodeur utilisé pour hacher le secret client avant stockage. */
    private final PasswordEncoder            passwordEncoder;

    /**
     * Construit le contrôleur avec ses collaborateurs.
     *
     * @param clientRepo      dépôt de persistance des clients OAuth2
     * @param passwordEncoder encodeur utilisé pour hacher les secrets clients
     */
    public OAuthClientController(RegisteredClientRepository clientRepo,
                                 PasswordEncoder passwordEncoder) {
        this.clientRepo      = clientRepo;
        this.passwordEncoder = passwordEncoder;
    }


    /**
     * Enregistre un nouveau client OAuth2 et génère son secret.
     *
     * <p>Le secret est produit aléatoirement, stocké sous forme hachée et renvoyé en clair
     * une seule fois dans la réponse : il ne peut plus être récupéré par la suite. Le client
     * est configuré pour l'authentification {@code client_secret_basic} et le grant
     * {@code client_credentials}, sans écran de consentement.</p>
     *
     * @param request identifiant, nom, portées demandées et durée de vie du jeton souhaitée
     * @return {@code 200 OK} avec le secret en clair et un rappel de le conserver
     */
    @PostMapping
    public ResponseEntity<ClientRegistrationResponse> registerClient(
            @Valid @RequestBody ClientRegistrationRequest request) {

        log.info("Registering new OAuth2 client: {}", request.clientId());

        String rawSecret = UUID.randomUUID().toString().replace("-", "");

        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(request.clientId())
                .clientSecret(passwordEncoder.encode(rawSecret))
                .clientName(request.clientName())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scopes(scopes -> scopes.addAll(request.scopes()))
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(
                                Duration.ofSeconds(request.tokenTtlSeconds() > 0
                                        ? request.tokenTtlSeconds() : 3600))
                        .build())
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .build())
                .build();

        clientRepo.save(client);
        log.info("OAuth2 client registered: clientId={}", request.clientId());

        return ResponseEntity.ok(new ClientRegistrationResponse(
                client.getId(),
                client.getClientId(),
                rawSecret,
                "Client registered. Save the client_secret — it will not be shown again."
        ));
    }


    /**
     * Retourne les informations publiques d'un client OAuth2 enregistré.
     *
     * <p>Le secret n'est jamais exposé par ce point d'entrée.</p>
     *
     * @param clientId identifiant du client recherché
     * @return {@code 200 OK} avec les informations du client, ou {@code 404 Not Found} s'il n'existe pas
     */
    @GetMapping("/{clientId}")
    public ResponseEntity<ClientInfoResponse> getClient(@PathVariable String clientId) {
        RegisteredClient client = clientRepo.findByClientId(clientId);
        if (client == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new ClientInfoResponse(
                client.getId(),
                client.getClientId(),
                client.getClientName(),
                client.getScopes().stream().toList(),
                client.getClientIdIssuedAt()
        ));
    }


    /**
     * Requête d'enregistrement d'un client OAuth2.
     *
     * @param clientId        identifiant technique du client, obligatoire
     * @param clientName      libellé lisible du client, obligatoire
     * @param scopes          portées accordées au client, au moins une
     * @param tokenTtlSeconds durée de vie du jeton d'accès en secondes ; toute valeur nulle
     *                        ou négative est remplacée par 3600
     */
    public record ClientRegistrationRequest(
            @NotBlank String       clientId,
            @NotBlank String       clientName,
            @NotEmpty List<String> scopes,
            long                   tokenTtlSeconds
    ) {}

    /**
     * Réponse retournée après l'enregistrement d'un client OAuth2.
     *
     * @param id           identifiant interne du client enregistré
     * @param clientId     identifiant technique du client
     * @param clientSecret secret en clair, communiqué une seule fois
     * @param message      avertissement invitant à conserver le secret
     */
    public record ClientRegistrationResponse(
            String id,
            String clientId,
            String clientSecret,
            String message
    ) {}

    /**
     * Informations publiques d'un client OAuth2 enregistré.
     *
     * @param id         identifiant interne du client
     * @param clientId   identifiant technique du client
     * @param clientName libellé lisible du client
     * @param scopes     portées accordées au client
     * @param issuedAt   date d'enregistrement du client
     */
    public record ClientInfoResponse(
            String              id,
            String              clientId,
            String              clientName,
            List<String>        scopes,
            java.time.Instant   issuedAt
    ) {}
}

