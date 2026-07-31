package wevioo.se.authms.Exceptions;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.FieldError;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gestionnaire d'exceptions global : convertit les exceptions remontées par les contrôleurs
 * en réponses HTTP normalisées au format RFC 7807 ({@link ProblemDetail}).
 *
 * <p>Chaque réponse comporte un statut, un titre, un message explicatif, un type identifiant
 * la catégorie d'erreur, l'URI de la requête fautive et un horodatage.</p>
 *
 * <p>Les messages retournés au client restent volontairement génériques sur les erreurs
 * d'authentification et les erreurs internes : le détail technique part dans les journaux,
 * pas dans la réponse.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Préfixe des URI de documentation associées aux types d'erreur. */
    private static final String BASE = "https://platform.bank.com/errors/";

    /** Constructeur par défaut, utilisé par Spring pour instancier ce gestionnaire. */
    public GlobalExceptionHandler() {
    }


    /**
     * Traite l'échec d'authentification par identifiants invalides.
     *
     * <p>Le message ne précise pas si c'est le nom d'utilisateur ou le mot de passe qui est
     * en cause, afin de ne pas permettre d'énumérer les comptes existants.</p>
     *
     * @param ex  exception levée par le gestionnaire d'authentification
     * @param req requête à l'origine de l'erreur
     * @return un {@link ProblemDetail} de statut {@code 401 Unauthorized}
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex, WebRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "Authentication Failed",
                "Invalid username or password", "authentication-failed", req);
    }

    /**
     * Traite la tentative de connexion à un compte désactivé.
     *
     * @param ex  exception levée par le gestionnaire d'authentification
     * @param req requête à l'origine de l'erreur
     * @return un {@link ProblemDetail} de statut {@code 403 Forbidden}
     */
    @ExceptionHandler(DisabledException.class)
    public ProblemDetail handleDisabled(DisabledException ex, WebRequest req) {
        return build(HttpStatus.FORBIDDEN, "Account Disabled",
                "Your account is disabled. Contact your administrator.", "account-disabled", req);
    }

    /**
     * Traite le rejet d'un jeton invalide.
     *
     * @param ex  exception portant le motif du rejet
     * @param req requête à l'origine de l'erreur
     * @return un {@link ProblemDetail} de statut {@code 401 Unauthorized}
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ProblemDetail handleInvalidToken(InvalidTokenException ex, WebRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid Token", ex.getMessage(), "invalid-token", req);
    }

    /**
     * Traite le rejet d'un jeton expiré.
     *
     * @param ex  exception portant le motif du rejet
     * @param req requête à l'origine de l'erreur
     * @return un {@link ProblemDetail} de statut {@code 401 Unauthorized}
     */
    @ExceptionHandler(TokenExpiredException.class)
    public ProblemDetail handleTokenExpired(TokenExpiredException ex, WebRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "Token Expired", ex.getMessage(), "token-expired", req);
    }


    /**
     * Traite la demande d'une ressource inexistante.
     *
     * @param ex  exception décrivant la ressource introuvable
     * @param req requête à l'origine de l'erreur
     * @return un {@link ProblemDetail} de statut {@code 404 Not Found}
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, WebRequest req) {
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), "not-found", req);
    }

    /**
     * Traite la violation d'une règle métier.
     *
     * @param ex  exception décrivant la règle non respectée
     * @param req requête à l'origine de l'erreur
     * @return un {@link ProblemDetail} de statut {@code 422 Unprocessable Entity}
     */
    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleValidation(ValidationException ex, WebRequest req) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "Validation Error",
                ex.getMessage(), "validation-error", req);
    }


    /**
     * Traite l'indisponibilité du microservice utilisateurs et journalise l'incident.
     *
     * @param ex  exception décrivant l'appel en échec
     * @param req requête à l'origine de l'erreur
     * @return un {@link ProblemDetail} de statut {@code 503 Service Unavailable}
     */
    @ExceptionHandler(UserServiceException.class)
    public ProblemDetail handleUserService(UserServiceException ex, WebRequest req) {
        log.error("UserMS unavailable: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                ex.getMessage(), "service-unavailable", req);
    }


    /**
     * Traite l'échec de validation des annotations portées par les DTO d'entrée.
     *
     * <p>Enrichit la réponse d'une propriété {@code fieldErrors} associant chaque champ fautif
     * à son message, ce qui permet au client d'annoter directement son formulaire. Lorsqu'un
     * même champ cumule plusieurs violations, seul le premier message est conservé.</p>
     *
     * @param ex      exception portant le résultat de la validation
     * @param headers en-têtes de la réponse
     * @param status  statut proposé par Spring, ici remplacé par {@code 422}
     * @param req     requête à l'origine de l'erreur
     * @return une réponse {@code 422 Unprocessable Entity} détaillant les champs en erreur
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest req) {

        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (first, second) -> first
                ));

        ProblemDetail problem = build(HttpStatus.UNPROCESSABLE_ENTITY,
                "Validation Failed", "One or more fields are invalid.", "validation-error", req);
        problem.setProperty("fieldErrors", fieldErrors);

        return ResponseEntity.unprocessableEntity().body(problem);
    }


    /**
     * Filet de sécurité traitant toute exception non prise en charge par un gestionnaire
     * plus spécifique.
     *
     * <p>La trace complète est journalisée, tandis que le client ne reçoit qu'un message
     * générique : les détails d'implémentation ne doivent pas fuiter dans la réponse.</p>
     *
     * @param ex  exception non anticipée
     * @param req requête à l'origine de l'erreur
     * @return un {@link ProblemDetail} de statut {@code 500 Internal Server Error}
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleAll(Exception ex, WebRequest req) {
        log.error("Unhandled exception [{}]: {}", req.getDescription(false), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred. Please contact support.", "internal-error", req);
    }

    // Builder

    /**
     * Assemble un {@link ProblemDetail} conforme à la RFC 7807.
     *
     * @param status statut HTTP de la réponse
     * @param title  intitulé court de l'erreur
     * @param detail message explicatif destiné au client
     * @param type   suffixe identifiant la catégorie d'erreur, concaténé au préfixe de documentation
     * @param req    requête à l'origine de l'erreur, dont l'URI alimente le champ {@code instance}
     * @return le descripteur d'erreur, horodaté à l'instant courant
     */
    private ProblemDetail build(HttpStatus status, String title,
                                String detail, String type, WebRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create(BASE + type));
        pd.setProperty("timestamp", Instant.now().toString());
        pd.setInstance(URI.create(req.getDescription(false).replace("uri=", "")));
        return pd;
    }
}

