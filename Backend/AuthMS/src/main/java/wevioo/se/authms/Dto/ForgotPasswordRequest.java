package wevioo.se.authms.Dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Requête de demande de réinitialisation de mot de passe.
 *
 * @param email adresse e-mail du compte concerné, vers laquelle le lien de réinitialisation
 *              sera envoyé si elle correspond à un compte existant
 */
public record ForgotPasswordRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email

) {}
