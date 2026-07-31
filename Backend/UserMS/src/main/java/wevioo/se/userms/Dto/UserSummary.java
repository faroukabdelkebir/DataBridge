package wevioo.se.userms.Dto;

import lombok.Builder;
import wevioo.se.userms.Entities.Role;

import java.time.Instant;
import java.util.UUID;

/**
 * Représentation allégée d'un utilisateur, utilisée dans les listes paginées.
 *
 * <p>Ce résumé ne conserve que les champs nécessaires à l'affichage d'un tableau
 * de résultats. Pour la vue complète d'un compte, il faut appeler l'endpoint de
 * consultation par identifiant, qui renvoie un {@link UserResponse}.</p>
 *
 * @param id        identifiant technique du compte
 * @param username  nom de connexion
 * @param email     adresse e-mail, normalisée en minuscules
 * @param role      rôle fonctionnel attribué au compte
 * @param active    {@code true} si le compte est actif
 * @param createdAt date de création du compte, critère de tri par défaut des
 *                  recherches
 * @see wevioo.se.userms.Services.UserService#searchUsers(String, Role, Boolean, org.springframework.data.domain.Pageable)
 */
@Builder
public record UserSummary(
        UUID id,
        String  username,
        String  email,
        Role role,
        boolean active,
        Instant createdAt
) {}
