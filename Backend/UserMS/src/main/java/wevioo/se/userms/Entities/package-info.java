/**
 * Modèle persistant du microservice.
 *
 * <p>{@link wevioo.se.userms.Entities.User} représente un compte de la
 * plateforme, stocké dans la table {@code platform_users}, et
 * {@link wevioo.se.userms.Entities.Role} l'énumération des rôles fonctionnels
 * qu'il peut porter.</p>
 *
 * <p>Ces classes sont réservées aux couches service et persistance : elles sont
 * converties en DTO par {@link wevioo.se.userms.Mapper.UserMapper} avant toute
 * exposition, afin que le hash du mot de passe ne quitte jamais le service par
 * inadvertance.</p>
 */
package wevioo.se.userms.Entities;
