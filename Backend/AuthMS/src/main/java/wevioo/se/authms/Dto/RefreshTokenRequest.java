package wevioo.se.authms.Dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Requête portant un jeton de rafraîchissement.
 *
 * <p>Utilisée aussi bien pour obtenir un nouveau jeton d'accès que pour révoquer une session
 * lors de la déconnexion.</p>
 *
 * @param refreshToken jeton de rafraîchissement émis lors d'une authentification antérieure
 */
public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {}
