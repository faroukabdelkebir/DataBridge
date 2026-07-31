package com.t24extractor.configuration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.mock;

/**
 * Règles d'autorisation effectivement appliquées par la chaîne de filtres.
 *
 * Le test précédent couvre la conversion des scopes ; celui-ci vérifie ce qui
 * en est fait : qui peut réellement déclencher une extraction. C'est la seule
 * barrière entre un appelant non autorisé et le lancement d'un job batch sur
 * les bases de production.
 */
@SpringJUnitWebConfig
@ContextConfiguration(classes = SecurityFilterChainTest.TestConfig.class)
class SecurityFilterChainTest {

    private MockMvc mockMvc;

    @Autowired
    void setUp(WebApplicationContext context) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void healthAndInfoEndpointsArePublicForProbes() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }

    @Test
    void jobEndpointsRejectAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/v1/start")).andExpect(status().isUnauthorized());
    }

    @Test
    void jobEndpointsRejectTokensWithoutTheBatchExecuteScope() throws Exception {
        mockMvc.perform(get("/api/v1/start")
                        .with(jwt().jwt(builder -> builder.claim("scope", "batch:read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void jobEndpointsAcceptTokensCarryingTheBatchExecuteScope() throws Exception {
        mockMvc.perform(get("/api/v1/start")
                        .with(jwt().jwt(builder -> builder.claim("scope", "batch:execute"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "SCOPE_batch:execute"))))
                .andExpect(status().isOk());
    }

    @Test
    void anyOtherEndpointStillRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/interne")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/interne")
                        .with(jwt().jwt(builder -> builder.claim("scope", "batch:read"))))
                .andExpect(status().isOk());
    }

    @Configuration
    @EnableWebSecurity
    @Import({SecurityConfig.class, WebMvcAutoConfiguration.class})
    static class TestConfig {

        /** L'émetteur réel (AuthMS) n'est pas joignable : le décodage est simulé. */
        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }

        @Bean
        StubEndpoints stubEndpoints() {
            return new StubEndpoints();
        }
    }

    @RestController
    static class StubEndpoints {

        @GetMapping({"/actuator/health", "/actuator/info"})
        String actuator() {
            return "UP";
        }

        @GetMapping("/api/v1/start")
        String start() {
            return "started";
        }

        @GetMapping("/interne")
        String internal() {
            return "ok";
        }
    }
}
