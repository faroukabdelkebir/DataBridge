package wevioo.se.userms.Entities;

/**
 * Rôle fonctionnel attribué à un utilisateur de la plateforme.
 *
 * <p>Le rôle est stocké en base sous forme de chaîne
 * ({@link jakarta.persistence.EnumType#STRING}) afin que l'ajout ou le
 * réordonnancement d'une valeur ne corrompe pas les données existantes.</p>
 *
 * <p>À ne pas confondre avec les <em>scopes</em> OAuth2 (par exemple
 * {@code user:write}) : le rôle décrit ce qu'est l'utilisateur métier, tandis
 * que le scope décrit ce que le client appelant a le droit de faire.</p>
 *
 * @see User
 */
public enum Role {
    /** Administrateur : accès complet à la gestion de la plateforme. */
    ADMIN,
    /** Superviseur : consultation et suivi de l'activité, sans droits d'administration. */
    MONITOR,
    /** Visiteur : accès en lecture seule, périmètre le plus restreint. */
    VISITOR
}
