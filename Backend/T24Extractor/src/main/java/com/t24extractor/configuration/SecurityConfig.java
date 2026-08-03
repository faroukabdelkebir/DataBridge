package com.t24extractor.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * L'extracteur est un RESOURCE SERVER OAuth2 : il valide les JWT émis par
 * AuthMS (signature vérifiée via le endpoint JWKS) et n'autorise le
 * lancement des jobs qu'aux clients porteurs du scope "batch:execute".
 *
 * Il n'est PAS un client OAuth2 : il n'appelle aucune API protégée
 * (accès direct JDBC à Oracle/T24 et SQL Server/BI).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        // Tous les lancements de jobs exigent le scope batch:execute.
                        // Seuls les clients machine autorisés (ex: frontend-bff, après
                        // vérification du rôle ADMIN de l'utilisateur) le possèdent.
                        .requestMatchers("/api/v1/**").hasAuthority("SCOPE_batch:execute")

                        .anyRequest().authenticated()
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        ));

        return http.build();
    }

    /**
     * Même convertisseur que UserMS : extrait les scopes du claim "scope"
     * (liste ou chaîne séparée par des espaces) et les préfixe par SCOPE_.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        converter.setPrincipalClaimName("client_id");
        return converter;
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        return extractScopes(jwt).stream()
                .map(scope -> (GrantedAuthority) new SimpleGrantedAuthority("SCOPE_" + scope))
                .toList();
    }

    private List<String> extractScopes(Jwt jwt) {
        // NB : ne pas utiliser getClaimAsStringList ici. Ce helper convertit une
        // chaîne en liste d'UN seul élément, si bien qu'un claim standard
        // "scope": "batch:execute batch:read" (RFC 6749) produisait l'autorité
        // unique "SCOPE_batch:execute batch:read" — et donc un 403 sur /api/v1/**.
        Object rawScope = jwt.getClaim("scope");

        if (rawScope instanceof Collection<?> scopes) {
            return scopes.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .filter(scope -> !scope.isBlank())
                    .toList();
        }

        if (rawScope instanceof String scopeString && !scopeString.isBlank()) {
            return List.of(scopeString.trim().split("\\s+"));
        }

        return Collections.emptyList();
    }
}