package wevioo.se.userms.Controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wevioo.se.userms.Config.SecurityConfig;
import wevioo.se.userms.Dto.UserCredentialsResponse;
import wevioo.se.userms.Exceptions.GlobalExceptionHandler;
import wevioo.se.userms.Services.UserService;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de la tranche web de {@link InternalUserController}.
 *
 * <p>L'enjeu principal est le cloisonnement des scopes : ces endpoints exposent
 * des empreintes de mots de passe et ne doivent être accessibles qu'avec
 * {@code user:credentials:read} ou {@code user:credentials:write}, jamais avec
 * les scopes utilisateur ordinaires.</p>
 *
 * <p>{@link SecurityConfig} et {@link GlobalExceptionHandler} sont importés
 * explicitement, car {@code @WebMvcTest} ne charge pas les {@code @Configuration}
 * du projet. La couche métier est remplacée par un mock.</p>
 */
@WebMvcTest(InternalUserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class InternalUserControllerWebMvcTest {

    /** Client HTTP simulé, sans conteneur de servlets réel. */
    @Autowired
    private MockMvc mockMvc;

    /** Couche métier simulée, pilotée test par test. */
    @MockitoBean
    private UserService userService;

    /**
     * Vérifie qu'une recherche par nom de connexion, faite avec le scope de
     * lecture des identifiants, renvoie bien l'empreinte du mot de passe.
     *
     * @throws Exception si l'exécution de la requête simulée échoue
     */
    @Test
    void getCredentialsByUsernameRequiresCredentialsReadScope() throws Exception {
        UserCredentialsResponse response = new UserCredentialsResponse(
                UUID.randomUUID(), "john", "john@example.com", "{bcrypt}hash", "ADMIN", true
        );
        when(userService.findCredentialsByUsername("john")).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/v1/users/internal/credentials")
                        .param("username", "john")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_user:credentials:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("john"))
                .andExpect(jsonPath("$.passwordHash").value("{bcrypt}hash"));
    }

    /**
     * Vérifie qu'une requête sans critère de recherche est rejetée en
     * {@code 400 Bad Request}.
     *
     * @throws Exception si l'exécution de la requête simulée échoue
     */
    @Test
    void getCredentialsReturnsBadRequestWhenNoLookupParameterIsProvided() throws Exception {
        mockMvc.perform(get("/api/v1/users/internal/credentials")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_user:credentials:read"))))
                .andExpect(status().isBadRequest());
    }

    /**
     * Vérifie que le scope ordinaire {@code user:read} ne donne pas accès aux
     * identifiants.
     *
     * @throws Exception si l'exécution de la requête simulée échoue
     */
    @Test
    void getCredentialsReturnsForbiddenForRegularUserReadScope() throws Exception {
        mockMvc.perform(get("/api/v1/users/internal/credentials")
                        .param("username", "john")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_user:read"))))
                .andExpect(status().isForbidden());
    }

    /**
     * Vérifie que le changement d'empreinte transmet la valeur reçue au service
     * sans la transformer.
     *
     * @throws Exception si l'exécution de la requête simulée échoue
     */
    @Test
    void updatePasswordRequiresCredentialsWriteScope() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/users/internal/{id}/password", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_user:credentials:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "passwordHash": "{bcrypt}new-hash"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(userService).updatePassword(id, "{bcrypt}new-hash");
    }
}
