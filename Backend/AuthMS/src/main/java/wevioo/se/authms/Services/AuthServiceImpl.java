package wevioo.se.authms.Services;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wevioo.se.authms.Config.AppProperties;
import wevioo.se.authms.Dto.ChangePasswordRequest;
import wevioo.se.authms.Dto.ForgotPasswordRequest;
import wevioo.se.authms.Dto.LoginRequest;
import wevioo.se.authms.Dto.RefreshTokenRequest;
import wevioo.se.authms.Dto.ResetPasswordRequest;
import wevioo.se.authms.Dto.AuthResponse;
import wevioo.se.authms.Dto.MessageResponse;
import wevioo.se.authms.Entities.AuthEventType;
import wevioo.se.authms.Entities.PasswordResetToken;
import wevioo.se.authms.Entities.RefreshToken;
import wevioo.se.authms.Exceptions.*;
import wevioo.se.authms.Repositories.PasswordResetTokenRepository;
import wevioo.se.authms.Repositories.RefreshTokenRepository;
import wevioo.se.authms.UserMS.UserPrincipal;
import wevioo.se.authms.UserMS.UserServiceClient;
import wevioo.se.authms.UserMS.UserServiceClient.UserCredentials;
import wevioo.se.authms.Services.AuditService;
import wevioo.se.authms.Services.AuthService;
import wevioo.se.authms.Services.MailService;
import wevioo.se.authms.Services.Utils.JwtService;
import wevioo.se.authms.Services.Utils.TokenHashUtil;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;


/**
 * Implémentation de la logique métier d'authentification.
 *
 * <p>Orchestre les identifiants détenus par UserMS, les jetons persistés localement, la
 * journalisation d'audit et les notifications par courriel.</p>
 *
 * <p>Principes de sécurité appliqués :</p>
 * <ul>
 *   <li>les jetons opaques ne sont stockés que sous forme d'empreinte ;</li>
 *   <li>les jetons de rafraîchissement subissent une rotation, et la réutilisation d'un jeton
 *       déjà consommé révoque toutes les sessions de l'utilisateur ;</li>
 *   <li>tout changement de mot de passe invalide les sessions existantes ;</li>
 *   <li>les messages d'erreur ne permettent pas de distinguer un compte inexistant d'un
 *       mot de passe erroné.</li>
 * </ul>
 *
 * @see AuthService
 */
@Slf4j
@Service("authService")
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    /** Client d'accès aux identifiants détenus par UserMS. */
    private final UserServiceClient            userServiceClient;

    /** Dépôt des jetons de rafraîchissement. */
    private final RefreshTokenRepository       refreshTokenRepo;

    /** Dépôt des jetons de réinitialisation de mot de passe. */
    private final PasswordResetTokenRepository resetTokenRepo;

    /** Service d'émission et de lecture des jetons d'accès JWT. */
    private final JwtService                   jwtService;

    /** Encodeur employé pour vérifier et produire les empreintes de mots de passe. */
    private final PasswordEncoder              passwordEncoder;

    /** Service de journalisation des évènements d'authentification. */
    private final AuditService                 auditService;

    /** Service d'envoi des courriels liés aux mots de passe. */
    private final MailService                  mailService;

    /** Configuration applicative : durées de validité et URL du front-end. */
    private final AppProperties                props;

    /**
     * Construit le service avec l'ensemble de ses collaborateurs.
     *
     * @param userServiceClient client d'accès aux identifiants détenus par UserMS
     * @param refreshTokenRepo  dépôt des jetons de rafraîchissement
     * @param resetTokenRepo    dépôt des jetons de réinitialisation de mot de passe
     * @param jwtService        service d'émission et de lecture des jetons d'accès
     * @param passwordEncoder   encodeur de mots de passe
     * @param auditService      service de journalisation des évènements d'authentification
     * @param mailService       service d'envoi des courriels
     * @param props             configuration applicative
     */
    public AuthServiceImpl(UserServiceClient userServiceClient,
                           RefreshTokenRepository refreshTokenRepo,
                           PasswordResetTokenRepository resetTokenRepo,
                           JwtService jwtService,
                           PasswordEncoder passwordEncoder,
                           AuditService auditService,
                           MailService mailService,
                           AppProperties props) {
        this.userServiceClient = userServiceClient;
        this.refreshTokenRepo  = refreshTokenRepo;
        this.resetTokenRepo    = resetTokenRepo;
        this.jwtService        = jwtService;
        this.passwordEncoder   = passwordEncoder;
        this.auditService      = auditService;
        this.mailService       = mailService;
        this.props             = props;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Vérifie successivement l'existence du compte, son activation et le mot de passe, puis
     * émet un jeton d'accès ainsi qu'un jeton de rafraîchissement dont seule l'empreinte est
     * conservée. Chaque échec est audité avec son motif exact, tandis que le message renvoyé
     * au client reste identique dans tous les cas : le détail sert à l'analyse de sécurité,
     * pas à l'appelant.</p>
     *
     * @throws BadCredentialsException si le compte est inconnu ou le mot de passe erroné
     * @throws DisabledException       si le compte est désactivé
     */
    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        log.debug("Login attempt: username={}", request.username());

        UserCredentials creds = userServiceClient.findByUsername(request.username())
                .orElseThrow(() -> {
                    auditService.log(AuthEventType.LOGIN_FAILURE, request.username(),
                            false, "User not found", httpRequest);
                    return new BadCredentialsException("Invalid username or password");
                });

        if (!creds.active()) {
            auditService.log(AuthEventType.LOGIN_FAILURE, request.username(),
                    false, "Account disabled", httpRequest);
            throw new DisabledException("Account is disabled. Contact your administrator.");
        }

        if (!passwordEncoder.matches(request.password(), creds.passwordHash())) {
            auditService.log(AuthEventType.LOGIN_FAILURE, request.username(),
                    false, "Bad credentials", httpRequest);
            throw new BadCredentialsException("Invalid username or password");
        }

        UserPrincipal principal   = toPrincipal(creds);
        String        accessToken  = jwtService.generateAccessToken(principal);
        String        rawRefresh   = TokenHashUtil.generateRaw();
        String        hashedRefresh = TokenHashUtil.hashToken(rawRefresh);

        refreshTokenRepo.save(RefreshToken.builder()
                .token(hashedRefresh)
                .userId(creds.id())
                .expiresAt(Instant.now().plusMillis(props.getJwt().getRefreshTokenExpirationMs()))
                .build());

        auditService.log(AuthEventType.LOGIN_SUCCESS, request.username(), true,
                "Login successful", httpRequest);

        return AuthResponse.of(accessToken, rawRefresh,
                props.getJwt().getAccessTokenExpirationMs(),
                creds.id(), creds.username(), creds.email(), creds.role());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Un jeton introuvable n'est pas signalé comme une erreur : la session étant de toute
     * façon close, l'opération est idempotente et peut être rejouée sans effet de bord.</p>
     */
    @Override
    @Transactional
    public MessageResponse logout(String rawRefreshToken, HttpServletRequest httpRequest) {
        String hashed = TokenHashUtil.hashToken(rawRefreshToken);
        refreshTokenRepo.findByToken(hashed).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepo.save(rt);
            auditService.log(AuthEventType.LOGOUT, null, true, "Token revoked", httpRequest);
        });
        return MessageResponse.of("Logged out successfully.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Révoque en une seule opération tous les jetons de rafraîchissement de l'utilisateur.</p>
     */
    @Override
    @Transactional
    public MessageResponse logoutAll(UUID userId, HttpServletRequest httpRequest) {
        refreshTokenRepo.revokeAllByUserId(userId);
        auditService.log(AuthEventType.LOGOUT, null, true,
                "All sessions revoked for userId=" + userId, httpRequest);
        return MessageResponse.of("All sessions terminated successfully.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Applique la rotation des jetons : l'ancien jeton est révoqué et un nouveau couple est
     * émis. La présentation d'un jeton déjà révoqué signale une compromission probable — le
     * jeton légitime ayant été rejoué par un tiers — et entraîne la révocation de toutes les
     * sessions de l'utilisateur.</p>
     *
     * <p>Le compte est revalidé auprès de UserMS à chaque rafraîchissement : une désactivation
     * intervenue entre-temps prend effet immédiatement.</p>
     *
     * @throws InvalidTokenException     si le jeton est inconnu ou si une réutilisation est détectée
     * @throws TokenExpiredException     si le jeton est expiré
     * @throws ResourceNotFoundException si le compte associé n'existe plus
     * @throws DisabledException         si le compte a été désactivé
     */
    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        String hashed = TokenHashUtil.hashToken(request.refreshToken());

        RefreshToken rt = refreshTokenRepo.findByToken(hashed)
                .orElseThrow(() -> {
                    auditService.log(AuthEventType.TOKEN_REFRESH_FAILURE, null, false,
                            "Token not found", httpRequest);
                    return new InvalidTokenException("Invalid refresh token.");
                });

        if (rt.isRevoked()) {
            log.warn("Refresh token reuse detected! Revoking all sessions for userId={}", rt.getUserId());
            refreshTokenRepo.revokeAllByUserId(rt.getUserId());
            auditService.log(AuthEventType.TOKEN_REFRESH_FAILURE, null, false,
                    "Reuse detected – all sessions revoked", httpRequest);
            throw new InvalidTokenException("Token reuse detected. All sessions have been terminated.");
        }

        if (rt.isExpired()) {
            rt.setRevoked(true);
            refreshTokenRepo.save(rt);
            throw new TokenExpiredException("Session expired. Please login again.");
        }

        UserCredentials creds = userServiceClient.findById(rt.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!creds.active()) {
            rt.setRevoked(true);
            refreshTokenRepo.save(rt);
            throw new DisabledException("Account has been disabled.");
        }

        rt.setRevoked(true);
        refreshTokenRepo.save(rt);

        String newRaw    = TokenHashUtil.generateRaw();
        String newHashed = TokenHashUtil.hashToken(newRaw);
        refreshTokenRepo.save(RefreshToken.builder()
                .token(newHashed)
                .userId(rt.getUserId())
                .expiresAt(Instant.now().plusMillis(props.getJwt().getRefreshTokenExpirationMs()))
                .build());

        String newAccess = jwtService.generateAccessToken(
                creds.id(), creds.username(), creds.email(), creds.role());

        auditService.log(AuthEventType.TOKEN_REFRESH, creds.username(), true, "Token rotated", httpRequest);

        return AuthResponse.of(newAccess, newRaw,
                props.getJwt().getAccessTokenExpirationMs(),
                creds.id(), creds.username(), creds.email(), creds.role());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Invalide les demandes antérieures encore actives, émet un jeton à usage unique et en
     * envoie le lien par courriel. Le message retourné est constant, que l'adresse corresponde
     * ou non à un compte : l'API ne doit pas permettre de découvrir quels comptes existent.</p>
     */
    @Override
    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        final String msg = "If this email is registered, you will receive a reset link shortly.";

        userServiceClient.findByEmail(request.email()).ifPresent(creds -> {
            resetTokenRepo.invalidateAllByUserId(creds.id());

            String rawToken    = TokenHashUtil.generateRaw();
            String hashedToken = TokenHashUtil.hashToken(rawToken);
            Instant expiresAt  = Instant.now()
                    .plus(props.getResetPassword().getTokenExpirationMinutes(), ChronoUnit.MINUTES);

            resetTokenRepo.save(PasswordResetToken.builder()
                    .token(hashedToken)
                    .userId(creds.id())
                    .expiresAt(expiresAt)
                    .build());

            String resetLink = props.getResetPassword().getBaseUrl()
                    + "/reset-password?token=" + rawToken;

            mailService.sendPasswordResetEmail(creds.email(), creds.username(), resetLink,
                    props.getResetPassword().getTokenExpirationMinutes());

            auditService.log(AuthEventType.PASSWORD_RESET_REQUEST, creds.username(), true,
                    "Reset email sent", httpRequest);
        });

        return MessageResponse.of(msg);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Valide le jeton, met le mot de passe à jour auprès de UserMS, marque le jeton comme
     * consommé et révoque toutes les sessions en cours : une réinitialisation fait suite à un
     * oubli ou à une compromission, et les sessions ouvertes ne doivent pas y survivre.</p>
     *
     * @throws ValidationException   si les deux mots de passe saisis diffèrent
     * @throws InvalidTokenException si le jeton est inconnu ou déjà consommé
     * @throws TokenExpiredException si le jeton est expiré
     */
    @Override
    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request, HttpServletRequest httpRequest) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ValidationException("Passwords do not match.");
        }

        String hashed = TokenHashUtil.hashToken(request.token());

        PasswordResetToken prt = resetTokenRepo.findByToken(hashed)
                .orElseThrow(() -> {
                    auditService.log(AuthEventType.PASSWORD_RESET_FAILURE, null, false,
                            "Invalid token", httpRequest);
                    return new InvalidTokenException("Invalid or expired reset link.");
                });

        if (prt.isUsed()) {
            throw new InvalidTokenException("This reset link has already been used.");
        }

        if (prt.isExpired()) {
            prt.setUsed(true);
            resetTokenRepo.save(prt);
            throw new TokenExpiredException("This reset link has expired. Please request a new one.");
        }

        userServiceClient.updatePassword(prt.getUserId(), passwordEncoder.encode(request.newPassword()));

        prt.setUsed(true);
        resetTokenRepo.save(prt);
        refreshTokenRepo.revokeAllByUserId(prt.getUserId());

        auditService.log(AuthEventType.PASSWORD_RESET_SUCCESS, null, true,
                "Password reset for userId=" + prt.getUserId(), httpRequest);

        return MessageResponse.of("Password reset successfully. Please login with your new password.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Vérifie le mot de passe actuel, enregistre le nouveau auprès de UserMS, révoque les
     * sessions en cours et notifie l'utilisateur par courriel — ce qui lui permet de réagir
     * si le changement n'émane pas de lui.</p>
     *
     * @throws ValidationException       si les deux mots de passe saisis diffèrent
     * @throws ResourceNotFoundException si le compte n'existe pas
     * @throws BadCredentialsException   si le mot de passe actuel est erroné
     */
    @Override
    @Transactional
    public MessageResponse changePassword(UUID userId, ChangePasswordRequest request,
                                          HttpServletRequest httpRequest) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ValidationException("Passwords do not match.");
        }

        UserCredentials creds = userServiceClient.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        if (!passwordEncoder.matches(request.currentPassword(), creds.passwordHash())) {
            auditService.log(AuthEventType.PASSWORD_CHANGE, creds.username(), false,
                    "Wrong current password", httpRequest);
            throw new BadCredentialsException("Current password is incorrect.");
        }

        userServiceClient.updatePassword(userId, passwordEncoder.encode(request.newPassword()));
        refreshTokenRepo.revokeAllByUserId(userId);
        mailService.sendPasswordChangedNotification(creds.email(), creds.username());

        auditService.log(AuthEventType.PASSWORD_CHANGE, creds.username(), true,
                "Password changed", httpRequest);

        return MessageResponse.of("Password changed successfully. Please login again.");
    }

    /**
     * Purge quotidiennement les jetons devenus inutiles.
     *
     * <p>Exécutée chaque jour à minuit, cette tâche supprime définitivement les jetons de
     * rafraîchissement expirés ou révoqués, ainsi que les jetons de réinitialisation expirés
     * ou consommés, afin de contenir la croissance des tables.</p>
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepo.deleteExpiredAndRevoked(Instant.now());
        resetTokenRepo.deleteExpiredAndUsed(Instant.now());
        log.info("Scheduled token cleanup completed");
    }

    /**
     * Convertit les identifiants retournés par UserMS en principal de sécurité.
     *
     * @param creds identifiants issus de UserMS
     * @return le principal correspondant, porteur de l'empreinte du mot de passe
     */
    private UserPrincipal toPrincipal(UserCredentials creds) {
        return UserPrincipal.builder()
                .userId(creds.id())
                .username(creds.username())
                .email(creds.email())
                .password(creds.passwordHash())
                .role(creds.role())
                .active(creds.active())
                .build();
    }
}
