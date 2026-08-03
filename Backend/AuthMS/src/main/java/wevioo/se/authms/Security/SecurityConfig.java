package wevioo.se.authms.Security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.util.StringUtils;
import wevioo.se.authms.Config.AppProperties;
import wevioo.se.authms.Security.JwtAuthenticationFilter;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;

/**
 * Configuration centrale de la sécurité applicative.
 *
 * <p>Déclare deux chaînes de filtres distinctes :</p>
 * <ol>
 *   <li>l'API REST et le BFF ({@code /api/**}, {@code /bff/**}, {@code /actuator/**}),
 *       sans état et authentifiée par JWT ;</li>
 *   <li>le serveur d'autorisation OAuth2, qui traite le reste des requêtes.</li>
 * </ol>
 *
 * <p>Assure également l'amorçage des clients OAuth2 internes et la fourniture de la clé RSA
 * de signature des jetons.</p>
 *
 * @see JwtAuthenticationFilter
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Filtre d'authentification par JWT inséré dans la chaîne de l'API. */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** Chargeur de ressources utilisé pour accéder au magasin de clés. */
    private final ResourceLoader resourceLoader;

    /**
     * Construit la configuration avec ses collaborateurs.
     *
     * @param jwtAuthenticationFilter filtre d'authentification par JWT
     * @param resourceLoader          chargeur de ressources donnant accès au magasin de clés
     */
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ResourceLoader resourceLoader) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.resourceLoader          = resourceLoader;
    }


    /** Secret du client OAuth2 du service ETL. */
    @Value("${app.oauth2.clients.etl.secret}")
    private String etlClientSecret;

    /** Secret du client OAuth2 du BFF front-end. */
    @Value("${app.oauth2.clients.bff.secret}")
    private String bffClientSecret;

    /** Secret du client OAuth2 d'AuthMS lui-même, pour ses appels vers UserMS. */
    @Value("${app.oauth2.clients.auth.secret}")
    private String authServiceClientSecret;

    /** Emplacement du magasin de clés PKCS12 contenant la clé de signature ; vide en développement. */
    @Value("${app.oauth2.jwk.keystore-path:}")
    private String jwkKeystorePath;

    /** Mot de passe du magasin de clés et de la clé privée. */
    @Value("${app.oauth2.jwk.keystore-password:}")
    private String jwkKeystorePassword;

    /** Alias de la clé de signature dans le magasin de clés. */
    @Value("${app.oauth2.jwk.key-alias:}")
    private String jwkKeyAlias;


    /**
     * Chaîne de filtres de l'API REST et du BFF, évaluée en premier.
     *
     * <p>Session désactivée, CSRF et authentification par formulaire retirés : la sécurité
     * repose entièrement sur le JWT porté par chaque requête. Les points d'entrée publics
     * (connexion, rafraîchissement, parcours de mot de passe oublié, sondes de supervision)
     * sont ouverts ; les routes du BFF sont restreintes par rôle, et toute requête non
     * explicitement décrite exige une authentification.</p>
     *
     * @param http constructeur de configuration de sécurité HTTP
     * @return la chaîne de filtres appliquée à l'API et au BFF
     * @throws Exception si la configuration est invalide
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**", "/bff/**", "/actuator/**")
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.POST, "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password"
                        ).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()

                        .requestMatchers("/bff/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/bff/ai/api/m1/audit/**",
                                "/bff/ai/api/m1/audit-all/**",
                                "/bff/ai/api/m3/scheduler/recommend/**").hasAnyRole("ADMIN", "MONITOR")
                        .requestMatchers("/bff/ai/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/bff/batch/schema-source/**").hasAnyRole("ADMIN", "MONITOR")
                        .requestMatchers("/bff/batch/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/logout",
                                "/api/v1/auth/logout-all",
                                "/api/v1/auth/change-password"
                        ).authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()

                        .requestMatchers("/api/v1/auth/audit/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }


    /**
     * Chaîne de filtres du serveur d'autorisation OAuth2, appliquée aux requêtes non prises
     * en charge par {@link #apiFilterChain}.
     *
     * <p>Active la couche OpenID Connect et le serveur de ressources JWT. Les clients
     * attendant du HTML sont redirigés vers la page de connexion, tandis que les appels
     * programmatiques reçoivent une réponse d'erreur.</p>
     *
     * @param http constructeur de configuration de sécurité HTTP
     * @return la chaîne de filtres du serveur d'autorisation
     * @throws Exception si la configuration est invalide
     */
    @Bean
    @Order(2)
    public SecurityFilterChain oauth2ServerFilterChain(HttpSecurity http) throws Exception {

        http.with(new OAuth2AuthorizationServerConfigurer(), configurer ->
                configurer.oidc(Customizer.withDefaults())
        );

        http
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                        )
                )
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()));

        return http.build();
    }


    /**
     * Fournit le dépôt JDBC des clients OAuth2 et y amorce les clients internes.
     *
     * <p>Les clients ETL, BFF et AuthMS sont créés au démarrage s'ils n'existent pas encore ;
     * un client déjà présent est laissé intact, ce qui rend l'opération rejouable sans
     * écraser une configuration existante.</p>
     *
     * @param jdbcTemplate         accès JDBC à la base des clients
     * @param defaultTokenSettings réglages de jeton appliqués aux clients amorcés
     * @param passwordEncoder      encodeur utilisé pour hacher les secrets clients
     * @return le dépôt des clients OAuth2 enregistrés
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(
            JdbcTemplate jdbcTemplate,
            TokenSettings defaultTokenSettings,
            PasswordEncoder passwordEncoder) {

        JdbcRegisteredClientRepository repo = new JdbcRegisteredClientRepository(jdbcTemplate);

        seedIfAbsent(repo, buildEtlClient(defaultTokenSettings, passwordEncoder));
        seedIfAbsent(repo, buildFrontendBffClient(defaultTokenSettings, passwordEncoder));
        seedIfAbsent(repo, buildAuthServiceClient(defaultTokenSettings, passwordEncoder));

        return repo;
    }

    /**
     * Fournit le service de persistance des autorisations OAuth2 délivrées.
     *
     * <p>Le stockage en base, plutôt qu'en mémoire, permet aux autorisations de survivre à un
     * redémarrage et d'être partagées entre plusieurs instances.</p>
     *
     * @param jdbcTemplate accès JDBC à la base des autorisations
     * @param repo         dépôt des clients enregistrés
     * @return le service de gestion des autorisations
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository repo) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, repo);
    }

    /**
     * Réglages de jeton appliqués par défaut aux clients OAuth2.
     *
     * <p>Jeton d'accès valable une heure et rotation des jetons de rafraîchissement : un jeton
     * consommé n'est pas réutilisable.</p>
     *
     * @return les réglages de jeton par défaut
     */
    @Bean
    public TokenSettings defaultTokenSettings() {
        return TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofHours(1))
                .reuseRefreshTokens(false)
                .build();
    }


    /**
     * Réglages du serveur d'autorisation, dont l'émetteur publié dans les métadonnées OpenID
     * et la revendication {@code iss} des jetons.
     *
     * @param props configuration applicative fournissant l'URL de l'émetteur
     * @return les réglages du serveur d'autorisation
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings(AppProperties props) {
        return AuthorizationServerSettings.builder()
                .issuer(props.getOauth2().getIssuer())
                .build();
    }

    /**
     * Fournit le jeu de clés RSA servant à signer les jetons et exposé via l'endpoint JWKS.
     *
     * <p>La clé provient du magasin de clés lorsque celui-ci est configuré ; à défaut, une clé
     * éphémère est générée — pratique en développement, mais à proscrire en production
     * (voir {@link #generateEphemeralRsaKey()}).</p>
     *
     * @return la source de clés utilisée pour la signature et la vérification des jetons
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = StringUtils.hasText(jwkKeystorePath)
                ? loadRsaKeyFromKeystore()
                : generateEphemeralRsaKey();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    /**
     * Charge la paire de clés RSA de signature depuis le magasin PKCS12 configuré.
     *
     * @return la clé RSA, partie privée et partie publique, identifiée par l'alias configuré
     * @throws IllegalStateException si le magasin est illisible ou si l'alias est introuvable
     */
    private RSAKey loadRsaKeyFromKeystore() {
        try {
            Resource resource = resourceLoader.getResource(jwkKeystorePath);
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            char[] password = jwkKeystorePassword.toCharArray();
            try (InputStream is = resource.getInputStream()) {
                keyStore.load(is, password);
            }
            RSAPrivateKey privateKey = (RSAPrivateKey) keyStore.getKey(jwkKeyAlias, password);
            RSAPublicKey publicKey =
                    (RSAPublicKey) keyStore.getCertificate(jwkKeyAlias).getPublicKey();

            log.info("Loaded RSA signing key from keystore (alias={})", jwkKeyAlias);
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(jwkKeyAlias)
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to load RSA key from keystore at " + jwkKeystorePath, ex);
        }
    }

    /**
     * Génère une paire de clés RSA 2048 bits valable pour la durée de vie du processus.
     *
     * <p>Solution de repli lorsque aucun magasin de clés n'est configuré. Les jetons émis ne
     * survivent alors pas à un redémarrage et ne sont pas reconnus par les autres instances :
     * un magasin de clés est indispensable en production. Un avertissement est journalisé.</p>
     *
     * @return une clé RSA éphémère, dotée d'un identifiant aléatoire
     * @throws IllegalStateException si la génération échoue
     */
    private RSAKey generateEphemeralRsaKey() {
        log.warn("No JWK keystore configured (app.oauth2.jwk.keystore-path is empty). "
                + "Generating an EPHEMERAL RSA key: issued tokens will NOT survive a restart "
                + "and are invalid across instances. Configure a keystore before production.");
        KeyPair keyPair = generateRsaKeyPair();
        RSAPublicKey  publicKey  = (RSAPublicKey)  keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Fournit le décodeur chargé de vérifier les jetons émis par ce serveur d'autorisation.
     *
     * @param jwkSource source des clés de vérification
     * @return le décodeur de jetons JWT
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }


    /**
     * Enrichit les jetons émis des revendications {@code scope} et {@code client_id}.
     *
     * <p>Ces informations permettent aux services en aval d'appliquer leurs contrôles d'accès
     * sans interroger le serveur d'autorisation.</p>
     *
     * @return le personnalisateur appliqué à l'encodage de chaque jeton
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return context -> {
            if (context.getAuthorizedScopes() != null) {
                context.getClaims().claim("scope",    context.getAuthorizedScopes());
                context.getClaims().claim("client_id", context.getRegisteredClient().getClientId());
            }
        };
    }


    /**
     * Fournit l'encodeur de mots de passe et de secrets clients.
     *
     * <p>L'encodeur délégant préfixe chaque empreinte de l'algorithme employé, ce qui permet
     * de vérifier les empreintes anciennes tout en produisant les nouvelles avec l'algorithme
     * courant.</p>
     *
     * @return l'encodeur de mots de passe
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }


    /**
     * Construit la définition du client OAuth2 du service ETL.
     *
     * <p>Portées accordées : lecture et écriture des utilisateurs, lecture de la supervision.</p>
     *
     * @param ts              réglages de jeton à appliquer
     * @param passwordEncoder encodeur utilisé pour hacher le secret client
     * @return le client ETL prêt à être enregistré
     */
    private RegisteredClient buildEtlClient(TokenSettings ts, PasswordEncoder passwordEncoder) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("etl-service")
                .clientSecret(passwordEncoder.encode(etlClientSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("user:read")
                .scope("user:write")
                .scope("monitoring:read")
                .tokenSettings(ts)
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .build();
    }

    /**
     * Construit la définition du client OAuth2 du BFF front-end.
     *
     * <p>Portées les plus larges de l'écosystème, le BFF relayant les requêtes du front vers
     * l'ensemble des services applicatifs.</p>
     *
     * @param ts              réglages de jeton à appliquer
     * @param passwordEncoder encodeur utilisé pour hacher le secret client
     * @return le client BFF prêt à être enregistré
     */
    private RegisteredClient buildFrontendBffClient(TokenSettings ts, PasswordEncoder passwordEncoder) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("frontend-bff")
                .clientSecret(passwordEncoder.encode(bffClientSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("user:read")
                .scope("user:write")
                .scope("monitoring:read")
                .scope("batch:execute")
                .scope("ai:read")
                .scope("ai:execute")
                .tokenSettings(ts)
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .build();
    }

    /**
     * AuthMS lui-même est enregistré comme client OAuth2 (flow client_credentials)
     * pour ses appels service-à-service vers UserMS. Les scopes user:credentials:*
     * sont réservés à ce client : ni l'ETL ni le BFF ne peuvent lire des hashes
     * de mots de passe.
     *
     * <p>La durée de vie du jeton est volontairement réduite à 15 minutes, ces portées étant
     * les plus sensibles de l'écosystème.</p>
     *
     * @param ts              réglages de jeton par défaut, ici remplacés par une durée plus courte
     * @param passwordEncoder encodeur utilisé pour hacher le secret client
     * @return le client AuthMS prêt à être enregistré
     */
    private RegisteredClient buildAuthServiceClient(TokenSettings ts, PasswordEncoder passwordEncoder) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("auth-service")
                .clientSecret(passwordEncoder.encode(authServiceClientSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("user:credentials:read")
                .scope("user:credentials:write")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(15))
                        .build())
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .build();
    }

    /**
     * Enregistre un client OAuth2 uniquement s'il n'est pas déjà présent en base.
     *
     * @param repo   dépôt des clients enregistrés
     * @param client client candidat à l'enregistrement
     */
    private void seedIfAbsent(JdbcRegisteredClientRepository repo, RegisteredClient client) {
        if (repo.findByClientId(client.getClientId()) == null) {
            repo.save(client);
        }
    }

    /**
     * Génère une paire de clés RSA de 2048 bits.
     *
     * @return la paire de clés générée
     * @throws IllegalStateException si l'algorithme RSA est indisponible
     */
    private KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA key pair", ex);
        }
    }
}
