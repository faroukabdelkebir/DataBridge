/**
 * Contrats d'échange de l'API, implémentés sous forme de {@code record}
 * immuables.
 *
 * <p>Les charges utiles entrantes
 * ({@link wevioo.se.userms.Dto.CreateUserRequest},
 * {@link wevioo.se.userms.Dto.UpdateUserRequest}) portent les contraintes
 * Jakarta Bean Validation appliquées avant tout traitement métier. Les sorties
 * se déclinent en une vue complète
 * ({@link wevioo.se.userms.Dto.UserResponse}) et une vue allégée pour les listes
 * paginées ({@link wevioo.se.userms.Dto.UserSummary}).</p>
 *
 * <p>{@link wevioo.se.userms.Dto.UserCredentialsResponse} et
 * {@link wevioo.se.userms.Dto.PasswordUpdateRequest} sont les seuls DTO à
 * manipuler l'empreinte du mot de passe : leur usage est strictement limité aux
 * endpoints internes.</p>
 */
package wevioo.se.userms.Dto;
