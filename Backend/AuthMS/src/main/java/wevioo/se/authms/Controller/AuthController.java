package wevioo.se.authms.Controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import wevioo.se.authms.Dto.ChangePasswordRequest;
import wevioo.se.authms.Dto.ForgotPasswordRequest;
import wevioo.se.authms.Dto.LoginRequest;
import wevioo.se.authms.Dto.RefreshTokenRequest;
import wevioo.se.authms.Dto.ResetPasswordRequest;
import wevioo.se.authms.Dto.AuthResponse;
import wevioo.se.authms.Dto.MessageResponse;
import wevioo.se.authms.Entities.AuthAuditLog;
import wevioo.se.authms.Entities.AuthEventType;
import wevioo.se.authms.Repositories.AuthAuditLogRepository;
import wevioo.se.authms.UserMS.UserPrincipal;
import wevioo.se.authms.Services.AuthService;

import java.util.UUID;

/**
 * Contrôleur REST exposant les points d'entrée d'authentification sous {@code /api/v1/auth}.
 *
 * <p>Couvre l'ensemble du cycle de vie d'une session : connexion, déconnexion (simple ou sur
 * tous les appareils), rafraîchissement des jetons, gestion du mot de passe (oubli,
 * réinitialisation, changement), consultation du profil courant et lecture du journal d'audit.</p>
 *
 * <p>La logique métier est entièrement déléguée à {@link AuthService} ; ce contrôleur se limite
 * à la validation des requêtes et à la traduction en réponses HTTP. Les erreurs sont converties
 * par {@link wevioo.se.authms.Exceptions.GlobalExceptionHandler}.</p>
 *
 * @see AuthService
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /** Service portant la logique d'authentification. */
    private final AuthService            authService;

    /** Dépôt de consultation du journal des évènements d'authentification. */
    private final AuthAuditLogRepository auditLogRepo;

    /**
     * Construit le contrôleur avec ses collaborateurs.
     *
     * @param authService  service portant la logique d'authentification
     * @param auditLogRepo dépôt de consultation du journal d'audit
     */
    public AuthController(AuthService authService, AuthAuditLogRepository auditLogRepo) {
        this.authService  = authService;
        this.auditLogRepo = auditLogRepo;
    }


    /**
     * Authentifie un utilisateur à partir de ses identifiants et ouvre une session.
     *
     * @param request     identifiants de connexion (nom d'utilisateur et mot de passe)
     * @param httpRequest requête HTTP entrante, utilisée pour tracer l'adresse IP et le user-agent
     * @return {@code 200 OK} avec les jetons d'accès et de rafraîchissement ainsi que le profil
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        log.info("Login request: username={}", request.username());
        AuthResponse response = authService.login(request, httpRequest);
        return ResponseEntity.ok(response);
    }


    /**
     * Ferme la session courante en révoquant le jeton de rafraîchissement fourni.
     *
     * <p>Les autres sessions de l'utilisateur restent actives ; utiliser
     * {@link #logoutAll} pour toutes les fermer.</p>
     *
     * @param request     jeton de rafraîchissement à révoquer
     * @param httpRequest requête HTTP entrante, utilisée pour l'audit
     * @return {@code 200 OK} avec un message de confirmation
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> logout(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {

        return ResponseEntity.ok(authService.logout(request.refreshToken(), httpRequest));
    }


    /**
     * Révoque tous les jetons de rafraîchissement de l'utilisateur authentifié,
     * fermant ainsi ses sessions sur l'ensemble de ses appareils.
     *
     * @param principal   utilisateur authentifié, extrait du contexte de sécurité
     * @param httpRequest requête HTTP entrante, utilisée pour l'audit
     * @return {@code 200 OK} avec un message de confirmation
     */
    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> logoutAll(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        return ResponseEntity.ok(authService.logoutAll(principal.getUserId(), httpRequest));
    }


    /**
     * Émet un nouveau jeton d'accès à partir d'un jeton de rafraîchissement valide.
     *
     * <p>Ce point d'entrée est public : il n'exige pas de jeton d'accès valide, puisqu'il sert
     * justement à en obtenir un après expiration du précédent.</p>
     *
     * @param request     jeton de rafraîchissement à échanger
     * @param httpRequest requête HTTP entrante, utilisée pour l'audit
     * @return {@code 200 OK} avec le nouveau couple de jetons
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {

        return ResponseEntity.ok(authService.refresh(request, httpRequest));
    }


    /**
     * Déclenche l'envoi d'un lien de réinitialisation de mot de passe à l'adresse indiquée.
     *
     * <p>La réponse est volontairement identique que l'adresse existe ou non, afin de ne pas
     * révéler quels comptes sont enregistrés.</p>
     *
     * @param request     adresse e-mail du compte concerné
     * @param httpRequest requête HTTP entrante, utilisée pour l'audit
     * @return {@code 200 OK} avec un message neutre de confirmation
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {

        return ResponseEntity.ok(authService.forgotPassword(request, httpRequest));
    }


    /**
     * Définit un nouveau mot de passe à partir d'un jeton de réinitialisation reçu par e-mail.
     *
     * @param request     jeton de réinitialisation, nouveau mot de passe et sa confirmation
     * @param httpRequest requête HTTP entrante, utilisée pour l'audit
     * @return {@code 200 OK} avec un message de confirmation
     */
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {

        return ResponseEntity.ok(authService.resetPassword(request, httpRequest));
    }


    /**
     * Change le mot de passe de l'utilisateur authentifié, après vérification de l'ancien.
     *
     * @param principal   utilisateur authentifié, extrait du contexte de sécurité
     * @param request     mot de passe actuel, nouveau mot de passe et sa confirmation
     * @param httpRequest requête HTTP entrante, utilisée pour l'audit
     * @return {@code 200 OK} avec un message de confirmation
     */
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {

        return ResponseEntity.ok(
                authService.changePassword(principal.getUserId(), request, httpRequest));
    }


    /**
     * Retourne le profil de l'utilisateur associé au jeton d'accès présenté.
     *
     * <p>Sert notamment au front-end pour restaurer l'état de session au chargement.</p>
     *
     * @param principal utilisateur authentifié, extrait du contexte de sécurité
     * @return {@code 200 OK} avec l'identifiant, le nom d'utilisateur, l'e-mail et le rôle
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CurrentUserResponse> me(
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(new CurrentUserResponse(
                principal.getUserId(),
                principal.getUsername(),
                principal.getEmail(),
                principal.getRole()
        ));
    }


    /**
     * Consulte le journal des évènements d'authentification, paginé et filtrable.
     *
     * <p>Les deux filtres sont facultatifs et se combinent : fournir les deux restreint aux
     * évènements d'un type donné pour un utilisateur donné ; n'en fournir aucun retourne
     * l'intégralité du journal. Réservé aux administrateurs.</p>
     *
     * @param username  nom d'utilisateur à filtrer, ou {@code null} pour ne pas filtrer
     * @param eventType type d'évènement à filtrer, ou {@code null} pour ne pas filtrer
     * @param pageable  pagination et tri (20 éléments triés par date de création par défaut)
     * @return {@code 200 OK} avec la page d'entrées d'audit correspondantes
     */
    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuthAuditLog>> getAuditLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) AuthEventType eventType,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        Page<AuthAuditLog> logs;

        if (username != null && eventType != null) {
            logs = auditLogRepo.findByUsernameAndEventType(username, eventType, pageable);
        } else if (username != null) {
            logs = auditLogRepo.findByUsername(username, pageable);
        } else if (eventType != null) {
            logs = auditLogRepo.findByEventType(eventType, pageable);
        } else {
            logs = auditLogRepo.findAll(pageable);
        }

        return ResponseEntity.ok(logs);
    }



    /**
     * Profil de l'utilisateur authentifié renvoyé par {@link #me}.
     *
     * @param id       identifiant unique de l'utilisateur
     * @param username nom d'utilisateur
     * @param email    adresse e-mail
     * @param role     rôle applicatif (par exemple {@code ADMIN})
     */
    public record CurrentUserResponse(UUID id, String username, String email, String role) {}
}

