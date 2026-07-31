package com.t24extractor.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * L'accès aux endpoints /api/v1/** est conditionné à l'autorité
 * SCOPE_batch:execute. Ces tests couvrent la conversion des claims du JWT en
 * autorités : une régression ici ouvrirait ou bloquerait le lancement des jobs.
 */
class SecurityConfigTest {

    private final JwtAuthenticationConverter converter = new SecurityConfig().jwtAuthenticationConverter();

    @Test
    void extractsScopesFromListClaim() {
        var authentication = converter.convert(jwt(Map.of("scope", List.of("batch:execute", "batch:read"))));

        assertEquals(List.of("SCOPE_batch:execute", "SCOPE_batch:read"), scopes(authentication.getAuthorities()));
    }

    @Test
    void extractsScopesFromSpaceSeparatedStringClaim() {
        // Forme standard RFC 6749 : le claim "scope" est une chaîne délimitée par
        // des espaces. Elle doit produire une autorité par scope, sinon
        // hasAuthority("SCOPE_batch:execute") échoue et tous les jobs sont en 403.
        var authentication = converter.convert(jwt(Map.of("scope", "batch:execute batch:read")));

        assertEquals(List.of("SCOPE_batch:execute", "SCOPE_batch:read"), scopes(authentication.getAuthorities()));
    }

    @Test
    void toleratesIrregularSpacingInStringClaim() {
        var authentication = converter.convert(jwt(Map.of("scope", "  batch:execute   batch:read  ")));

        assertEquals(List.of("SCOPE_batch:execute", "SCOPE_batch:read"), scopes(authentication.getAuthorities()));
    }

    @Test
    void grantsSingleScopeFromStringClaim() {
        var authentication = converter.convert(jwt(Map.of("scope", "batch:execute")));

        assertEquals(List.of("SCOPE_batch:execute"), scopes(authentication.getAuthorities()));
    }

    @Test
    void grantsNoScopeWhenClaimIsAbsentOrBlank() {
        assertTrue(scopes(converter.convert(jwt(Map.of("sub", "frontend-bff"))).getAuthorities()).isEmpty());
        assertTrue(scopes(converter.convert(jwt(Map.of("scope", "   "))).getAuthorities()).isEmpty());
        assertTrue(scopes(converter.convert(jwt(Map.of("scope", List.of()))).getAuthorities()).isEmpty());
    }

    @Test
    void usesClientIdAsPrincipalName() {
        var authentication = converter.convert(
                jwt(Map.of("client_id", "frontend-bff", "scope", "batch:execute")));

        assertEquals("frontend-bff", authentication.getName());
    }

    @Test
    void doesNotGrantBatchExecuteToUnrelatedScopes() {
        var authentication = converter.convert(jwt(Map.of("scope", "profile email")));

        List<String> granted = scopes(authentication.getAuthorities());
        assertEquals(List.of("SCOPE_email", "SCOPE_profile"), granted);
        assertFalse(granted.contains("SCOPE_batch:execute"));
    }

    /**
     * Ne conserve que les autorités issues des scopes, triées.
     *
     * Le framework ajoute par ailleurs des autorités de facteur d'authentification
     * (FACTOR_BEARER) qui ne relèvent pas de SecurityConfig, et ne garantit pas
     * l'ordre de restitution : seul l'ensemble des scopes accordés fait foi.
     */
    private List<String> scopes(java.util.Collection<? extends GrantedAuthority> granted) {
        return granted.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("SCOPE_"))
                .sorted()
                .toList();
    }

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token-value", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"), claims);
    }
}
