package wevioo.se.authms.Services;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentMatcher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import wevioo.se.authms.Config.AppProperties;
import wevioo.se.authms.Dto.AuthResponse;
import wevioo.se.authms.Dto.ChangePasswordRequest;
import wevioo.se.authms.Dto.LoginRequest;
import wevioo.se.authms.Dto.MessageResponse;
import wevioo.se.authms.Dto.RefreshTokenRequest;
import wevioo.se.authms.Entities.AuthEventType;
import wevioo.se.authms.Entities.RefreshToken;
import wevioo.se.authms.Exceptions.InvalidTokenException;
import wevioo.se.authms.Repositories.PasswordResetTokenRepository;
import wevioo.se.authms.Repositories.RefreshTokenRepository;
import wevioo.se.authms.Services.Utils.JwtService;
import wevioo.se.authms.Services.Utils.TokenHashUtil;
import wevioo.se.authms.UserMS.UserPrincipal;
import wevioo.se.authms.UserMS.UserServiceClient;
import wevioo.se.authms.UserMS.UserServiceClient.UserCredentials;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link AuthServiceImpl}.
 *
 * <p>Toutes les dépendances sont simulées ; le service est instancié à la main afin de lui
 * fournir une configuration de test aux durées de validité réduites.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    /** Client UserMS simulé. */
    @Mock
    private UserServiceClient userServiceClient;

    /** Dépôt des jetons de rafraîchissement, simulé. */
    @Mock
    private RefreshTokenRepository refreshTokenRepo;

    /** Dépôt des jetons de réinitialisation, simulé. */
    @Mock
    private PasswordResetTokenRepository resetTokenRepo;

    /** Service JWT simulé. */
    @Mock
    private JwtService jwtService;

    /** Encodeur de mots de passe simulé. */
    @Mock
    private PasswordEncoder passwordEncoder;

    /** Service d'audit simulé, dont on vérifie les appels. */
    @Mock
    private AuditService auditService;

    /** Service d'envoi de courriels simulé. */
    @Mock
    private MailService mailService;

    /** Requête HTTP simulée, transmise aux appels d'audit. */
    @Mock
    private HttpServletRequest httpRequest;

    /** Configuration de test du service. */
    private AppProperties props;

    /** Service testé. */
    private AuthServiceImpl authService;

    /** Constructeur par défaut, utilisé par JUnit pour instancier la classe de test. */
    AuthServiceImplTest() {
    }

    /**
     * Instancie le service avec ses dépendances simulées et une configuration fixant la
     * validité du jeton d'accès à une minute et celle du jeton de rafraîchissement à dix.
     */
    @BeforeEach
    void setUp() {
        props = new AppProperties();
        props.getJwt().setAccessTokenExpirationMs(60_000L);
        props.getJwt().setRefreshTokenExpirationMs(600_000L);
        authService = new AuthServiceImpl(
                userServiceClient,
                refreshTokenRepo,
                resetTokenRepo,
                jwtService,
                passwordEncoder,
                auditService,
                mailService,
                props
        );
    }

    /**
     * Vérifie qu'une connexion réussie persiste bien l'<em>empreinte</em> du jeton de
     * rafraîchissement — et non sa forme en clair — tout en retournant celle-ci au client,
     * et que l'évènement est audité.
     */
    @Test
    void loginPersistsHashedRefreshTokenAndReturnsAuthResponse() {
        UUID userId = UUID.randomUUID();
        UserCredentials creds = new UserCredentials(
                userId, "alice", "alice@example.com", "{bcrypt}hash", "ADMIN", true);
        when(userServiceClient.findByUsername("alice")).thenReturn(Optional.of(creds));
        when(passwordEncoder.matches("secret", "{bcrypt}hash")).thenReturn(true);
        when(jwtService.generateAccessToken(any(UserPrincipal.class))).thenReturn("access.jwt");

        AuthResponse response = authService.login(new LoginRequest("alice", "secret"), httpRequest);

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepo).save(tokenCaptor.capture());
        RefreshToken saved = tokenCaptor.getValue();

        assertThat(response.accessToken()).isEqualTo("access.jwt");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(saved.getToken()).isEqualTo(TokenHashUtil.hashToken(response.refreshToken()));
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        assertThat(response.expiresIn()).isEqualTo(60);
        verify(auditService).log(AuthEventType.LOGIN_SUCCESS, "alice", true,
                "Login successful", httpRequest);
    }

    /**
     * Vérifie qu'un compte inconnu est rejeté sans vérification du mot de passe, avec le même
     * message que pour un mot de passe erroné.
     */
    @Test
    void loginRejectsUnknownUserWithoutCheckingPassword() {
        when(userServiceClient.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("missing", "secret"), httpRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");

        verify(passwordEncoder, never()).matches(any(), any());
        verify(auditService).log(AuthEventType.LOGIN_FAILURE, "missing", false,
                "User not found", httpRequest);
    }

    /**
     * Vérifie qu'un compte désactivé est rejeté avant même la vérification du mot de passe.
     */
    @Test
    void loginRejectsDisabledAccount() {
        UserCredentials creds = new UserCredentials(
                UUID.randomUUID(), "alice", "alice@example.com", "{bcrypt}hash", "USER", false);
        when(userServiceClient.findByUsername("alice")).thenReturn(Optional.of(creds));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "secret"), httpRequest))
                .isInstanceOf(DisabledException.class)
                .hasMessage("Account is disabled. Contact your administrator.");

        verify(passwordEncoder, never()).matches(any(), any());
        verify(auditService).log(AuthEventType.LOGIN_FAILURE, "alice", false,
                "Account disabled", httpRequest);
    }

    /**
     * Vérifie que la présentation d'un jeton déjà révoqué déclenche la révocation de toutes
     * les sessions de l'utilisateur, la réutilisation trahissant une compromission probable.
     */
    @Test
    void refreshDetectsReuseAndRevokesAllUserSessions() {
        UUID userId = UUID.randomUUID();
        String rawToken = "already-used";
        RefreshToken token = RefreshToken.builder()
                .token(TokenHashUtil.hashToken(rawToken))
                .userId(userId)
                .expiresAt(Instant.now().plusSeconds(60))
                .revoked(true)
                .build();
        when(refreshTokenRepo.findByToken(TokenHashUtil.hashToken(rawToken)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(rawToken), httpRequest))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Token reuse detected. All sessions have been terminated.");

        verify(refreshTokenRepo).revokeAllByUserId(userId);
        verify(auditService).log(eq(AuthEventType.TOKEN_REFRESH_FAILURE), eq(null), eq(false),
                argThat((ArgumentMatcher<String>) detail ->
                        detail != null && detail.contains("all sessions revoked")), eq(httpRequest));
    }

    /**
     * Vérifie qu'un changement de mot de passe transmet bien l'empreinte du nouveau mot de
     * passe à UserMS, révoque les sessions en cours et notifie l'utilisateur.
     */
    @Test
    void changePasswordEncodesNewPasswordRevokesSessionsAndSendsNotification() {
        UUID userId = UUID.randomUUID();
        UserCredentials creds = new UserCredentials(
                userId, "alice", "alice@example.com", "{bcrypt}old", "USER", true);
        when(userServiceClient.findById(userId)).thenReturn(Optional.of(creds));
        when(passwordEncoder.matches("old-password", "{bcrypt}old")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("{bcrypt}new");

        MessageResponse response = authService.changePassword(userId,
                new ChangePasswordRequest("old-password", "new-password", "new-password"),
                httpRequest);

        assertThat(response.message()).isEqualTo("Password changed successfully. Please login again.");
        verify(userServiceClient).updatePassword(userId, "{bcrypt}new");
        verify(refreshTokenRepo).revokeAllByUserId(userId);
        verify(mailService).sendPasswordChangedNotification("alice@example.com", "alice");
        verify(auditService).log(AuthEventType.PASSWORD_CHANGE, "alice", true,
                "Password changed", httpRequest);
    }

    /**
     * Vérifie que la tâche de purge sollicite bien les deux dépôts de jetons.
     */
    @Test
    void cleanupExpiredTokensDelegatesToRepositories() {
        authService.cleanupExpiredTokens();

        verify(refreshTokenRepo).deleteExpiredAndRevoked(any(Instant.class));
        verify(resetTokenRepo).deleteExpiredAndUsed(any(Instant.class));
    }
}
