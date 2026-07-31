package wevioo.se.authms.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/**
 * Identifiants soumis lors d'une tentative de connexion.
 *
 * <p>Les propriétés JSON inconnues sont ignorées, ce qui permet aux clients d'envoyer des
 * champs additionnels sans provoquer d'erreur de désérialisation.</p>
 *
 * @param username nom d'utilisateur
 * @param password mot de passe en clair, vérifié puis jamais conservé
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginRequest(

        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        String password

) {}