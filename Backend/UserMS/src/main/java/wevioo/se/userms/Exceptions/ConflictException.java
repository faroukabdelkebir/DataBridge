package wevioo.se.userms.Exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Signale qu'une opération entre en conflit avec l'état actuel des données.
 *
 * <p>Levée par la couche service lorsqu'un nom d'utilisateur ou une adresse
 * e-mail déjà pris est soumis, que ce soit à la création d'un compte ou lors du
 * changement d'e-mail. Elle est traduite en réponse {@code 409 Conflict} au
 * format {@code application/problem+json} par
 * {@link GlobalExceptionHandler#handleConflict}.</p>
 *
 * <p>Cette vérification applicative double la contrainte d'unicité posée en base
 * afin de renvoyer un message explicite plutôt qu'une erreur SQL brute ; la
 * contrainte reste le garde-fou en cas d'accès concurrents.</p>
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {
    /**
     * Construit l'exception avec le message décrivant le conflit rencontré.
     *
     * @param message description du conflit, par exemple
     *                {@code "Username already taken: ..."} ; ce texte est repris
     *                tel quel dans le champ {@code detail} de la réponse d'erreur
     */
    public ConflictException(String message) {
        super(message);
    }
}
