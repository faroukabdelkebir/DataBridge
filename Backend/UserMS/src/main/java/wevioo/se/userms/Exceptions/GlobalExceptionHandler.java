package wevioo.se.userms.Exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Traduit les exceptions remontées par les contrôleurs en réponses HTTP
 * normalisées.
 *
 * <p>Toutes les erreurs sont renvoyées au format {@code application/problem+json}
 * défini par la RFC 7807 ({@link ProblemDetail}), enrichi de deux propriétés :
 * un {@code timestamp} et un champ {@code type} pointant vers une URI de
 * documentation dérivée de {@value #BASE_TYPE}.</p>
 *
 * <p>Les erreurs de validation ajoutent en outre une carte {@code fieldErrors}
 * associant chaque champ invalide à son message.</p>
 *
 * <p>Principe de journalisation retenu : les erreurs imputables au client sont
 * tracées en {@code WARN} ou {@code DEBUG} sans pile d'appels, tandis que les
 * erreurs inattendues sont tracées en {@code ERROR} avec la pile complète.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Préfixe des URI de documentation renseignées dans le champ {@code type} des réponses d'erreur. */
    private static final String BASE_TYPE = "https://platform.wevioo.com/errors/";


    /**
     * Traite une ressource introuvable.
     *
     * @param ex      l'exception levée par la couche service
     * @param request la requête en cours, utilisée pour renseigner le champ {@code instance}
     * @return un {@link ProblemDetail} en {@code 404 Not Found} reprenant le message de l'exception
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, WebRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildProblemDetail(
                HttpStatus.NOT_FOUND,
                "Resource Not Found",
                ex.getMessage(),
                "not-found",
                request
        );
    }

    /**
     * Traite un conflit de données, typiquement un nom d'utilisateur ou un e-mail déjà pris.
     *
     * @param ex      l'exception levée par la couche service
     * @param request la requête en cours, utilisée pour renseigner le champ {@code instance}
     * @return un {@link ProblemDetail} en {@code 409 Conflict} reprenant le message de l'exception
     */
    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex, WebRequest request) {
        log.warn("Conflict: {}", ex.getMessage());
        return buildProblemDetail(
                HttpStatus.CONFLICT,
                "Conflict",
                ex.getMessage(),
                "conflict",
                request
        );
    }


    /**
     * Traite un refus d'autorisation : le jeton est valide mais ne porte pas les
     * scopes requis.
     *
     * <p>Le message renvoyé est générique et ne reprend pas celui de l'exception,
     * afin de ne pas divulguer les règles d'autorisation internes.</p>
     *
     * @param ex      l'exception levée par Spring Security
     * @param request la requête refusée
     * @return un {@link ProblemDetail} en {@code 403 Forbidden}
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        log.warn("Access denied: {}", request.getDescription(false));
        return buildProblemDetail(
                HttpStatus.FORBIDDEN,
                "Access Denied",
                "You do not have permission to perform this action.",
                "access-denied",
                request
        );
    }

    /**
     * Traite un jeton d'accès invalide, expiré ou mal signé.
     *
     * @param ex      l'exception levée par le resource server OAuth2
     * @param request la requête rejetée
     * @return un {@link ProblemDetail} en {@code 401 Unauthorized}
     */
    @ExceptionHandler(InvalidBearerTokenException.class)
    public ProblemDetail handleInvalidToken(InvalidBearerTokenException ex, WebRequest request) {
        log.warn("Invalid bearer token: {}", ex.getMessage());
        return buildProblemDetail(
                HttpStatus.UNAUTHORIZED,
                "Invalid Token",
                "The provided access token is invalid or expired.",
                "invalid-token",
                request
        );
    }


    /**
     * Traite l'échec de validation d'une charge utile annotée {@code @Valid}.
     *
     * <p>Redéfinit le comportement par défaut de
     * {@link ResponseEntityExceptionHandler} pour produire un
     * {@code 422 Unprocessable Entity} (plutôt qu'un {@code 400}) accompagné du
     * détail des champs fautifs. Lorsqu'un même champ viole plusieurs
     * contraintes, seul le premier message est conservé.</p>
     *
     * @param ex      l'exception de validation, porteuse du résultat de liaison
     * @param headers les en-têtes de la réponse en cours de construction
     * @param status  le statut proposé par Spring, ici volontairement remplacé
     * @param request la requête invalide
     * @return une réponse {@code 422} dont le corps est un {@link ProblemDetail}
     *         enrichi de la propriété {@code fieldErrors}
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (existing, replacement) -> existing   // keep first error per field
                ));

        ProblemDetail problem = buildProblemDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Validation Failed",
                "One or more fields are invalid.",
                "validation-error",
                request
        );
        problem.setProperty("fieldErrors", fieldErrors);

        log.debug("Validation failed: {}", fieldErrors);
        return ResponseEntity.unprocessableEntity().body(problem);
    }



    /**
     * Filet de sécurité pour toute exception non prise en charge par un
     * gestionnaire plus spécifique.
     *
     * <p>Le détail technique est journalisé avec sa pile d'appels mais n'est pas
     * renvoyé au client, qui reçoit un message générique.</p>
     *
     * @param ex      l'exception inattendue
     * @param request la requête ayant échoué
     * @return un {@link ProblemDetail} en {@code 500 Internal Server Error}
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleAll(Exception ex, WebRequest request) {
        log.error("Unhandled exception on {}: {}", request.getDescription(false), ex.getMessage(), ex);
        return buildProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. Please contact support.",
                "internal-error",
                request
        );
    }



    /**
     * Fabrique un {@link ProblemDetail} homogène pour tous les gestionnaires.
     *
     * @param status  statut HTTP à renvoyer
     * @param title   libellé court et stable de la catégorie d'erreur
     * @param detail  message lisible décrivant le problème rencontré
     * @param type    segment d'URI ajouté à {@value #BASE_TYPE} pour identifier
     *                le type d'erreur, par exemple {@code "not-found"}
     * @param request requête à l'origine de l'erreur, dont l'URI alimente le
     *                champ {@code instance}
     * @return le corps d'erreur normalisé, horodaté à l'instant de sa création
     */
    private ProblemDetail buildProblemDetail(
            HttpStatus status,
            String title,
            String detail,
            String type,
            WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(BASE_TYPE + type));
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        return problem;
    }
}
