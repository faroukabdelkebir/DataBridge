/**
 * Contrôleurs REST du microservice.
 *
 * <p>Deux surfaces d'API cohabitent :</p>
 * <ul>
 *   <li>{@link wevioo.se.userms.Controller.UserController}, sous
 *       {@code /api/v1/users}, pour la gestion courante des comptes ;</li>
 *   <li>{@link wevioo.se.userms.Controller.InternalUserController}, sous
 *       {@code /api/v1/users/internal}, réservé au service d'authentification
 *       car il expose les empreintes de mots de passe.</li>
 * </ul>
 *
 * <p>Les contrôleurs ne portent aucune logique métier : ils valident la charge
 * utile, délèguent à {@link wevioo.se.userms.Services.UserService} et
 * choisissent le statut HTTP. Les exceptions sont traduites en réponses
 * normalisées par {@link wevioo.se.userms.Exceptions.GlobalExceptionHandler}.</p>
 */
package wevioo.se.userms.Controller;
