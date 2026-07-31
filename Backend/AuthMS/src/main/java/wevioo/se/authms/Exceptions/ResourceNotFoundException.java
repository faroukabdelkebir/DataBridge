package wevioo.se.authms.Exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Signale qu'une ressource demandée est introuvable.
 *
 * <p>Se traduit par une réponse {@code 404 Not Found}.</p>
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Construit l'exception avec la description de la ressource manquante.
     *
     * @param message description de la ressource recherchée et du critère employé
     */
    public ResourceNotFoundException(String message) { super(message); }
}