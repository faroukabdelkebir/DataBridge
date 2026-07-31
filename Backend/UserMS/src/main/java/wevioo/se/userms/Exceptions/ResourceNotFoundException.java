package wevioo.se.userms.Exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Signale qu'une ressource demandée n'existe pas.
 *
 * <p>Levée par la couche service lorsqu'une recherche par identifiant ou par nom
 * d'utilisateur ne renvoie aucun résultat. Elle est traduite en réponse
 * {@code 404 Not Found} au format {@code application/problem+json} par
 * {@link GlobalExceptionHandler#handleNotFound}.</p>
 *
 * <p>Exception non contrôlée : elle traverse la pile sans être déclarée, et
 * provoque le retrait de la transaction en cours.</p>
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {
    /**
     * Construit l'exception avec le message décrivant la ressource introuvable.
     *
     * @param message description de la ressource introuvable, par exemple
     *                {@code "User not found: id=..."} ; ce texte est repris tel
     *                quel dans le champ {@code detail} de la réponse d'erreur
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
