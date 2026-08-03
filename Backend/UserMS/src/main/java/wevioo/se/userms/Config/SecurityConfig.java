package wevioo.se.userms.Config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Configuration de la sécurité HTTP du microservice.
 *
 * <p>Le service est un <em>resource server</em> OAuth2 : il ne délivre aucun
 * jeton et n'ouvre aucune session. Chaque requête doit porter un JWT signé par
 * le serveur d'autorisation, validé grâce à la clé publique récupérée sur le
 * {@code jwk-set-uri} configuré dans {@code application.yml}.</p>
 *
 * <p>L'autorisation repose sur les <em>scopes</em> portés par le jeton :</p>
 * <ul>
 *   <li>{@code user:read} — consultation des utilisateurs ;</li>
 *   <li>{@code user:write} — création, modification et suppression ;</li>
 *   <li>{@code user:credentials:read} / {@code user:credentials:write} —
 *       endpoints internes exposant les hashes de mots de passe, réservés au
 *       service d'authentification.</li>
 * </ul>
 *
 * <p>{@link EnableMethodSecurity} permet en complément les contrôles
 * {@code @PreAuthorize} posés sur les méthodes des contrôleurs.</p>
 *
 * @see wevioo.se.userms.Controller.InternalUserController
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {


    /**
     * Construit la chaîne de filtres de sécurité appliquée à toutes les requêtes.
     *
     * <p>La configuration est volontairement sans état : sessions désactivées,
     * CSRF désactivé (inutile sans cookie de session), ainsi que les
     * authentifications par formulaire et HTTP Basic. Seuls les endpoints
     * {@code /actuator/health} et {@code /actuator/info} restent publics pour
     * permettre les sondes de disponibilité.</p>
     *
     * @param http constructeur de configuration fourni par Spring Security
     * @return la chaîne de filtres à enregistrer dans le contexte
     * @throws Exception si la construction de la configuration échoue
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()

                        // Endpoints internes (hashes de mots de passe) : réservés au
                        // client OAuth2 "auth-service" via ses scopes dédiés.
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/internal/**")
                        .hasAuthority("SCOPE_user:credentials:read")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/internal/**")
                        .hasAuthority("SCOPE_user:credentials:write")

                        .requestMatchers(HttpMethod.GET, "/api/v1/users/**").hasAnyAuthority(
                                "SCOPE_user:read", "SCOPE_user:write"
                        )
                        .requestMatchers(HttpMethod.POST,   "/api/v1/users/**").hasAuthority("SCOPE_user:write")
                        .requestMatchers(HttpMethod.PUT,    "/api/v1/users/**").hasAuthority("SCOPE_user:write")
                        .requestMatchers(HttpMethod.PATCH,  "/api/v1/users/**").hasAuthority("SCOPE_user:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/**").hasAuthority("SCOPE_user:write")
                        .anyRequest().authenticated()
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        ));

        return http.build();
    }



    /**
     * Encodeur de mots de passe utilisé lors de la création et de la mise à jour
     * d'un compte.
     *
     * <p>La fabrique renvoie un encodeur <em>délégant</em> : le hash produit est
     * préfixé par l'algorithme employé (par exemple {@code {bcrypt}...}), ce qui
     * permet de vérifier d'anciens hashes tout en migrant progressivement vers un
     * algorithme plus récent.</p>
     *
     * @return l'encodeur délégant, avec BCrypt comme algorithme par défaut
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }


    /**
     * Convertisseur transformant un JWT validé en objet d'authentification Spring.
     *
     * <p>Il délègue l'extraction des habilitations à
     * {@link #extractAuthorities(Jwt)} et retient la revendication
     * {@code client_id} comme nom du <em>principal</em>, car les appelants sont
     * des services et non des utilisateurs finaux.</p>
     *
     * @return le convertisseur à brancher sur le resource server
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        converter.setPrincipalClaimName("client_id");
        return converter;
    }

    /**
     * Convertit les revendications du jeton en habilitations Spring Security.
     *
     * <p>Chaque scope devient une autorité préfixée par {@code SCOPE_} et chaque
     * rôle une autorité préfixée par {@code ROLE_}, conformément aux conventions
     * attendues par {@code hasAuthority(...)} et {@code hasRole(...)}.</p>
     *
     * @param jwt le jeton déjà validé par le resource server
     * @return les habilitations issues des scopes et des rôles, éventuellement vide
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Stream<GrantedAuthority> scopeAuthorities = extractScopes(jwt)
                .stream()
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope));

        Stream<GrantedAuthority> roleAuthorities = extractRoles(jwt)
                .stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role));

        return Stream.concat(scopeAuthorities, roleAuthorities).toList();
    }

    /**
     * Lit les rôles portés par le jeton.
     *
     * <p>La revendication {@code roles} (tableau) est privilégiée ; à défaut, la
     * revendication {@code role} (valeur unique) est utilisée, afin de rester
     * compatible avec les jetons émis par différents serveurs d'autorisation.</p>
     *
     * @param jwt le jeton à inspecter
     * @return la liste des rôles, vide si aucune des deux revendications n'est exploitable
     */
    private List<String> extractRoles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null && !roles.isEmpty()) {
            return roles;
        }

        return Optional.ofNullable(jwt.getClaimAsString("role"))
                .filter(role -> !role.isBlank())
                .map(List::of)
                .orElseGet(Collections::emptyList);
    }

    /**
     * Lit les scopes portés par le jeton.
     *
     * <p>Trois représentations sont acceptées, dans cet ordre : {@code scope}
     * sous forme de tableau, {@code scope} sous forme de chaîne séparée par des
     * espaces (format standard OAuth2), puis {@code scp} sous forme de tableau.</p>
     *
     * @param jwt le jeton à inspecter
     * @return la liste des scopes, vide si aucune représentation n'est présente
     */
    private List<String> extractScopes(Jwt jwt) {
        List<String> scopeList = jwt.getClaimAsStringList("scope");
        if (scopeList != null && !scopeList.isEmpty()) {
            return scopeList;
        }
        String scopeString = jwt.getClaimAsString("scope");
        if (scopeString != null && !scopeString.isBlank()) {
            return List.of(scopeString.split(" "));
        }
        List<String> scpList = jwt.getClaimAsStringList("scp");
        return scpList != null ? scpList : Collections.emptyList();
    }
}
