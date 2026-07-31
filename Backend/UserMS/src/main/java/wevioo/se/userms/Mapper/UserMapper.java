package wevioo.se.userms.Mapper;

import org.springframework.stereotype.Component;
import wevioo.se.userms.Dto.UserResponse;
import wevioo.se.userms.Dto.UserSummary;
import wevioo.se.userms.Entities.User;

/**
 * Convertit l'entité {@link User} vers les DTO exposés par l'API.
 *
 * <p>Ce mapping est écrit à la main plutôt que généré, afin de garder un
 * contrôle explicite sur les champs publiés : le hash du mot de passe n'est
 * repris dans aucune des deux conversions, ce qui garantit qu'il ne peut pas
 * fuiter par les endpoints publics.</p>
 *
 * <p>Les conversions dans l'autre sens (DTO vers entité) sont réalisées
 * directement par la couche service, car elles s'accompagnent de règles métier
 * comme l'encodage du mot de passe ou la normalisation de l'e-mail.</p>
 *
 * @see wevioo.se.userms.Services.UserServiceImpl
 */
@Component
public class UserMapper {

    /**
     * Convertit une entité en représentation détaillée.
     *
     * @param user l'entité à convertir, éventuellement {@code null}
     * @return la vue complète de l'utilisateur, ou {@code null} si {@code user} est {@code null}
     */
    public UserResponse toResponse(User user) {
        if (user == null) return null;
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    /**
     * Convertit une entité en représentation allégée, destinée aux listes paginées.
     *
     * <p>Les champs peu utiles en liste (prénom, nom, horodatages de mise à jour
     * et de dernière connexion) sont volontairement omis pour réduire la taille
     * des réponses.</p>
     *
     * @param user l'entité à convertir, éventuellement {@code null}
     * @return le résumé de l'utilisateur, ou {@code null} si {@code user} est {@code null}
     */
    public UserSummary toSummary(User user) {
        if (user == null) return null;
        return UserSummary.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
