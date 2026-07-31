package wevioo.se.authms.Exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Signale un jeton valide dans sa forme mais dont la date d'expiration est dépassée.
 *
 * <p>Se traduit par une réponse {@code 401 Unauthorized}. Cette distinction avec
 * {@link InvalidTokenException} permet au client de savoir qu'un rafraîchissement suffit
 * là où un jeton invalide impose une nouvelle authentification.</p>
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class TokenExpiredException extends RuntimeException {

    /**
     * Construit l'exception avec le motif du rejet.
     *
     * @param message description du jeton concerné et de son expiration
     */
    public TokenExpiredException(String message) { super(message); }
}