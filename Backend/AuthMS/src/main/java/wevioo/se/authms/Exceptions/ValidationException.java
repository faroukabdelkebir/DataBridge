package wevioo.se.authms.Exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Signale une règle métier non respectée, au-delà des contraintes déclaratives portées par
 * les DTO — par exemple deux mots de passe qui ne concordent pas.
 *
 * <p>Se traduit par une réponse {@code 422 Unprocessable Entity}.</p>
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class ValidationException extends RuntimeException {

    /**
     * Construit l'exception avec la description de la règle violée.
     *
     * @param message description de la règle violée
     */
    public ValidationException(String message) { super(message); }
}
