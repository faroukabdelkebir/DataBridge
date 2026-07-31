package wevioo.se.userms.Controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wevioo.se.userms.Config.SecurityConfig;
import wevioo.se.userms.Dto.UserResponse;
import wevioo.se.userms.Dto.UserSummary;
import wevioo.se.userms.Entities.Role;
import wevioo.se.userms.Exceptions.GlobalExceptionHandler;
import wevioo.se.userms.Services.UserService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de la tranche web de {@link UserController}.
 *
 * <p>Couvrent trois aspects indissociables de la couche HTTP : le respect des
 * scopes OAuth2, la traduction des erreurs de validation en réponses
 * {@code 422}, et la transmission correcte des paramètres de recherche et de
 * pagination à la couche métier.</p>
 *
 * <p>{@link SecurityConfig} et {@link GlobalExceptionHandler} sont importés
 * explicitement, car {@code @WebMvcTest} ne charge pas les {@code @Configuration}
 * du projet. La couche métier est remplacée par un mock, si bien que seul le
 * comportement du contrôleur est vérifié.</p>
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UserControllerWebMvcTest {

    /** Client HTTP simulé, sans conteneur de servlets réel. */
    @Autowired
    private MockMvc mockMvc;

    /** Couche métier simulée, pilotée test par test. */
    @MockitoBean
    private UserService userService;

    /**
     * Vérifie qu'une création réussie renvoie {@code 201} avec un en-tête
     * {@code Location} pointant vers la ressource créée.
     *
     * @throws Exception si l'exécution de la requête simulée échoue
     */
    @Test
    void createUserReturnsCreatedLocationWhenScopeAllowsWrite() throws Exception {
        UUID id = UUID.randomUUID();
        UserResponse response = userResponse(id, "john", "john@example.com", Role.ADMIN, true);
        when(userService.createUser(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_user:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "john",
                                  "email": "john@example.com",
                                  "password": "Password1!",
                                  "firstName": "John",
                                  "lastName": "Doe",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/users/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.username").value("john"));
    }

    /**
     * Vérifie qu'une charge utile violant plusieurs contraintes produit un
     * {@code 422} détaillant chaque champ fautif.
     *
     * @throws Exception si l'exécution de la requête simulée échoue
     */
    @Test
    void createUserReturnsUnprocessableEntityWhenPayloadIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_user:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "ab",
                                  "email": "not-an-email",
                                  "password": "weak",
                                  "role": null
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.role").exists());
    }

    /**
     * Vérifie qu'une requête sans jeton est rejetée en {@code 401}.
     *
     * @throws Exception si l'exécution de la requête simulée échoue
     */
    @Test
    void getUserByIdRequiresJwt() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Vérifie qu'un jeton valide mais porteur d'un scope hors sujet est rejeté
     * en {@code 403}.
     *
     * @throws Exception si l'exécution de la requête simulée échoue
     */
    @Test
    void getUserByIdReturnsForbiddenWithoutReadOrWriteScope() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_profile:read"))))
                .andExpect(status().isForbidden());
    }

    /**
     * Vérifie que les filtres de recherche et la taille de page sont bien
     * convertis puis transmis au service.
     *
     * @throws Exception si l'exécution de la requête simulée échoue
     */
    @Test
    void searchUsersPassesFiltersAndPaginationToService() throws Exception {
        UserSummary summary = UserSummary.builder()
                .id(UUID.randomUUID())
                .username("alice")
                .email("alice@example.com")
                .role(Role.MONITOR)
                .active(true)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        when(userService.searchUsers(eq("ali"), eq(Role.MONITOR), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 5), 1));

        mockMvc.perform(get("/api/v1/users")
                        .param("search", "ali")
                        .param("role", "MONITOR")
                        .param("active", "true")
                        .param("size", "5")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_user:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("alice"))
                .andExpect(jsonPath("$.content[0].role").value("MONITOR"));

        verify(userService).searchUsers(eq("ali"), eq(Role.MONITOR), eq(true), any());
    }

    /**
     * Vérifie que la désactivation renvoie {@code 204} et atteint bien le service.
     *
     * @throws Exception si l'exécution de la requête simulée échoue
     */
    @Test
    void deactivateUserReturnsNoContentForWriteScope() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/users/{id}/deactivate", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_user:write"))))
                .andExpect(status().isNoContent());

        verify(userService).deactivateUser(id);
    }

    /**
     * Vérifie que le scope de lecture seule ne permet pas de supprimer un compte.
     *
     * @throws Exception si l'exécution de la requête simulée échoue
     */
    @Test
    void deleteUserRequiresWriteScope() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{id}", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_user:read"))))
                .andExpect(status().isForbidden());
    }

    /**
     * Fabrique une réponse utilisateur complète pour les besoins des tests.
     *
     * @param id       identifiant du compte
     * @param username nom de connexion
     * @param email    adresse e-mail
     * @param role     rôle attribué
     * @param active   statut d'activation
     * @return la réponse simulée, avec des horodatages fixes
     */
    private UserResponse userResponse(UUID id, String username, String email, Role role, boolean active) {
        return UserResponse.builder()
                .id(id)
                .username(username)
                .email(email)
                .firstName("John")
                .lastName("Doe")
                .role(role)
                .active(active)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-02T00:00:00Z"))
                .build();
    }
}
