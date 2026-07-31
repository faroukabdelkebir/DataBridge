package wevioo.se.authms.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import wevioo.se.authms.Dto.AuthResponse;
import wevioo.se.authms.Dto.LoginRequest;
import wevioo.se.authms.Repositories.AuthAuditLogRepository;
import wevioo.se.authms.Services.AuthService;

import java.util.UUID;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de la couche web de {@link AuthController}.
 *
 * <p>Montés en configuration autonome : seul le contrôleur est instancié, ses collaborateurs
 * étant simulés. Le contexte Spring complet n'est donc pas chargé.</p>
 */
class AuthControllerTest {

    /** Client de test simulant les appels HTTP au contrôleur. */
    private MockMvc mockMvc;

    /** Sérialiseur JSON, doté du module de prise en charge des types date-heure. */
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /** Service métier simulé. */
    private AuthService authService;

    /** Dépôt d'audit simulé. */
    private AuthAuditLogRepository auditLogRepo;

    /** Constructeur par défaut, utilisé par JUnit pour instancier la classe de test. */
    AuthControllerTest() {
    }

    /**
     * Assemble le contrôleur et son environnement de test : validation des DTO, résolution
     * de la pagination et conversion JSON.
     */
    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        auditLogRepo = mock(AuthAuditLogRepository.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService, auditLogRepo))
                .setValidator(validator)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    /**
     * Vérifie que la connexion retourne la charge utile attendue, notamment la conversion de
     * la durée de validité en secondes et la présence du type de jeton.
     *
     * @throws Exception si la requête simulée échoue
     */
    @Test
    void loginEndpointReturnsAuthPayload() throws Exception {
        AuthResponse response = AuthResponse.of(
                "access.jwt",
                "refresh-token",
                60_000,
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "alice",
                "alice@example.com",
                "ADMIN"
        );
        when(authService.login(any(LoginRequest.class), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("alice", "secret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(60))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        verify(authService).login(any(LoginRequest.class), any());
    }

    /**
     * Vérifie qu'une requête de connexion aux champs vides est rejetée par la validation.
     *
     * <p>Le statut attendu est {@code 400} : en configuration autonome, le gestionnaire
     * d'exceptions global qui traduirait l'erreur en {@code 422} n'est pas enregistré.</p>
     *
     * @throws Exception si la requête simulée échoue
     */
    @Test
    void loginEndpointValidatesRequiredFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    /**
     * Vérifie qu'en l'absence de filtre, la consultation du journal d'audit interroge
     * l'intégralité des entrées.
     *
     * @throws Exception si la requête simulée échoue
     */
    @Test
    @SuppressWarnings("unchecked")
    void auditEndpointQueriesAllLogsWhenNoFilterIsProvided() throws Exception {
        when(auditLogRepo.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/auth/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        verify(auditLogRepo).findAll(any(Pageable.class));
    }
}
