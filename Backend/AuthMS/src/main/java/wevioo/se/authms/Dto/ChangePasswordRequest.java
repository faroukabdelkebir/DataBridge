package wevioo.se.authms.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Requête de changement de mot de passe par un utilisateur authentifié.
 *
 * <p>Le nouveau mot de passe doit compter de 8 à 128 caractères et contenir au moins une
 * minuscule, une majuscule, un chiffre et un caractère spécial parmi {@code @$!%*?&}.
 * La concordance entre {@code newPassword} et {@code confirmPassword} n'est pas vérifiable
 * par annotation : elle est contrôlée dans la couche service.</p>
 *
 * @param currentPassword mot de passe actuel, exigé pour confirmer l'identité de l'utilisateur
 * @param newPassword     nouveau mot de passe, soumis à la politique de complexité
 * @param confirmPassword répétition du nouveau mot de passe
 */
public record ChangePasswordRequest(

        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 128)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
                message = "Password must contain uppercase, lowercase, digit and special character"
        )
        String newPassword,

        @NotBlank(message = "Confirmation is required")
        String confirmPassword

) {}
