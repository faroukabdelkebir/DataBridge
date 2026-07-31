package wevioo.se.authms.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Requête de réinitialisation de mot de passe à partir d'un jeton reçu par e-mail.
 *
 * <p>Le nouveau mot de passe doit compter de 8 à 128 caractères et contenir au moins une
 * minuscule, une majuscule, un chiffre et un caractère spécial parmi {@code @$!%*?&}.
 * La concordance entre {@code newPassword} et {@code confirmPassword} est contrôlée dans
 * la couche service.</p>
 *
 * @param token           jeton de réinitialisation à usage unique, transmis par e-mail
 * @param newPassword     nouveau mot de passe, soumis à la politique de complexité
 * @param confirmPassword répétition du nouveau mot de passe
 */
public record ResetPasswordRequest(

        @NotBlank(message = "Token is required")
        String token,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 128)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
                message = "Password must contain uppercase, lowercase, digit and special character"
        )
        String newPassword,

        @NotBlank(message = "Password confirmation is required")
        String confirmPassword

) {}
