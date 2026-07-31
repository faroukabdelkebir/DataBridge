package wevioo.se.userms.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import lombok.Builder;
import wevioo.se.userms.Entities.Role;

/**
 * Charge utile de création d'un utilisateur.
 *
 * <p>Tous les champs obligatoires sont validés par Jakarta Bean Validation avant
 * d'atteindre la couche service : une charge utile invalide produit une réponse
 * {@code 422 Unprocessable Entity} détaillant les erreurs champ par champ, via
 * {@link wevioo.se.userms.Exceptions.GlobalExceptionHandler}.</p>
 *
 * <p>Le mot de passe est transmis en clair sur le canal HTTPS puis encodé par le
 * service avant enregistrement ; il n'est jamais stocké tel quel.</p>
 *
 * <p>Les propriétés JSON inconnues sont ignorées, ce qui permet à un client
 * d'envoyer des champs supplémentaires sans provoquer d'erreur.</p>
 *
 * @param username  nom de connexion souhaité : 3 à 60 caractères, limités aux
 *                  lettres, chiffres, points, tirets et tirets bas ; doit être
 *                  disponible
 * @param email     adresse e-mail valide de 150 caractères maximum ; doit être
 *                  disponible et sera normalisée en minuscules
 * @param password  mot de passe en clair : 8 à 128 caractères, comprenant au
 *                  moins une minuscule, une majuscule, un chiffre et un
 *                  caractère spécial parmi {@code @$!%*?&}
 * @param firstName prénom, facultatif, 80 caractères maximum
 * @param lastName  nom de famille, facultatif, 80 caractères maximum
 * @param role      rôle fonctionnel à attribuer au compte, obligatoire
 * @see wevioo.se.userms.Services.UserService#createUser(CreateUserRequest)
 */
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateUserRequest(

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 60, message = "Username must be between 3 and 60 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username can only contain letters, digits, dots, hyphens, and underscores")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 150)
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
                message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
        )
        String password,

        @Size(max = 80)
        String firstName,

        @Size(max = 80)
        String lastName,

        @NotNull(message = "Role is required")
        Role role


) {}
