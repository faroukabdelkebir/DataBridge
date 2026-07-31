package wevioo.se.authms.Exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Signale un jeton inexploitable : signature invalide, format incorrect, jeton inconnu
 * en base ou déjà révoqué.
 *
 * <p>Se traduit par une réponse {@code 401 Unauthorized}. Pour un jeton correct mais
 * périmé, voir {@link TokenExpiredException}.</p>
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidTokenException extends RuntimeException {

    /**
     * Construit l'exception avec le motif du rejet.
     *
     * @param message description de la raison pour laquelle le jeton est rejeté
     */
    public InvalidTokenException(String message) { super(message); }
}
