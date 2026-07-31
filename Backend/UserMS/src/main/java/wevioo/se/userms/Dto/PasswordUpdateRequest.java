package wevioo.se.userms.Dto;

/**
 * Charge utile interne de remplacement du mot de passe d'un utilisateur.
 *
 * <p>Contrairement à {@link UpdateUserRequest}, ce DTO transporte une empreinte
 * <strong>déjà calculée</strong> et non un mot de passe en clair : il est
 * réservé au service d'authentification, qui reste maître de l'encodage. Le
 * service utilisateur se contente de stocker la valeur reçue telle quelle.</p>
 *
 * <p>Il n'est accepté que sur l'endpoint interne protégé par le scope
 * {@code user:credentials:write}.</p>
 *
 * @param passwordHash empreinte du mot de passe, préfixée par l'algorithme
 *                     utilisé (par exemple {@code {bcrypt}...})
 * @see wevioo.se.userms.Controller.InternalUserController#updatePassword(java.util.UUID, PasswordUpdateRequest)
 */
public record PasswordUpdateRequest(String passwordHash) {}
