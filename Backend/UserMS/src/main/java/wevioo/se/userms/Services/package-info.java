/**
 * Couche métier du microservice.
 *
 * <p>{@link wevioo.se.userms.Services.UserService} définit le contrat et
 * {@link wevioo.se.userms.Services.UserServiceImpl} l'implémente. C'est ici que
 * sont portées les règles applicatives — unicité du nom de connexion et de
 * l'e-mail, normalisation de l'e-mail en minuscules, encodage des mots de passe,
 * idempotence de l'activation et de la désactivation — ainsi que les frontières
 * transactionnelles.</p>
 *
 * <p>La couche n'échange que des DTO : l'entité
 * {@link wevioo.se.userms.Entities.User} reste confinée en dessous.</p>
 */
package wevioo.se.userms.Services;
