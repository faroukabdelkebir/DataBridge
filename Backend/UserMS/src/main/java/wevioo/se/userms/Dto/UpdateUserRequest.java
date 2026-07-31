package wevioo.se.userms.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import wevioo.se.userms.Entities.Role;

/**
 * Charge utile de mise à jour partielle d'un utilisateur.
 *
 * <p>Sémantique {@code PATCH} : tous les champs sont facultatifs et seuls ceux
 * explicitement renseignés sont appliqués. Un champ laissé à {@code null}
 * conserve sa valeur actuelle en base — il n'est donc pas possible d'effacer un
 * prénom ou un nom via ce DTO.</p>
 *
 * <p>Le nom d'utilisateur ne figure pas volontairement dans cette charge utile :
 * il est immuable une fois le compte créé.</p>
 *
 * <p>Les contraintes de validation sont identiques à celles de
 * {@link CreateUserRequest}, à la nuance près qu'elles ne s'appliquent qu'aux
 * champs présents.</p>
 *
 * @param email     nouvelle adresse e-mail ; sa disponibilité est revérifiée et
 *                  elle est normalisée en minuscules
 * @param password  nouveau mot de passe en clair, encodé par le service avant
 *                  enregistrement
 * @param firstName nouveau prénom, 80 caractères maximum
 * @param lastName  nouveau nom de famille, 80 caractères maximum
 * @param role      nouveau rôle fonctionnel
 * @param active    nouveau statut d'activation du compte
 * @see wevioo.se.userms.Services.UserService#updateUser(java.util.UUID, UpdateUserRequest)
 */
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateUserRequest(

        @Email(message = "Must be a valid email address")
        @Size(max = 150)
        String email,

        @Size(min = 8, max = 128)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
                message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
        )
        String password,

        @Size(max = 80)
        String firstName,

        @Size(max = 80)
        String lastName,

        Role role,

        Boolean active

) {}
