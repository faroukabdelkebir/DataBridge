package wevioo.se.authms.Exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Signale l'échec d'un appel au microservice utilisateurs (UserMS) : service injoignable,
 * délai dépassé ou réponse en erreur.
 *
 * <p>Se traduit par une réponse {@code 503 Service Unavailable}, l'indisponibilité étant
 * imputable à une dépendance et non à la requête du client.</p>
 *
 * @see wevioo.se.authms.UserMS.UserServiceClient
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class UserServiceException extends RuntimeException {

    /**
     * Construit l'exception avec la description de l'échec.
     *
     * @param message description de l'appel en échec et de sa cause
     */
    public UserServiceException(String message) { super(message); }
}
