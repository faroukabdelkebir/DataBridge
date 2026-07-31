package wevioo.se.authms.Dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * Réponse retournée après une authentification ou un rafraîchissement réussi.
 *
 * <p>Transporte le couple de jetons ainsi que les informations de profil dont le client a
 * besoin immédiatement, ce qui lui évite un appel supplémentaire à {@code /me}. Les champs
 * {@code null} sont omis de la sérialisation JSON.</p>
 *
 * @param accessToken  jeton d'accès JWT à présenter dans l'en-tête {@code Authorization}
 * @param refreshToken jeton de rafraîchissement permettant d'obtenir un nouveau jeton d'accès
 * @param tokenType    type de jeton, toujours {@code Bearer}
 * @param expiresIn    durée de validité du jeton d'accès, exprimée en <strong>secondes</strong>
 * @param issuedAt     instant d'émission des jetons
 * @param userId       identifiant unique de l'utilisateur authentifié
 * @param username     nom d'utilisateur
 * @param email        adresse e-mail
 * @param role         rôle applicatif
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(

        String       accessToken,
        String       refreshToken,
        String       tokenType,
        long         expiresIn,
        Instant      issuedAt,
        UUID         userId,
        String       username,
        String       email,
        String       role

) {
    /**
     * Fabrique une réponse d'authentification complète.
     *
     * <p>Positionne {@code tokenType} à {@code Bearer}, horodate l'émission à l'instant courant
     * et convertit la durée de validité de millisecondes en secondes, unité attendue par les
     * clients OAuth2.</p>
     *
     * @param accessToken  jeton d'accès JWT
     * @param refreshToken jeton de rafraîchissement
     * @param expiresInMs  durée de validité du jeton d'accès en <strong>millisecondes</strong>
     * @param userId       identifiant unique de l'utilisateur
     * @param username     nom d'utilisateur
     * @param email        adresse e-mail
     * @param role         rôle applicatif
     * @return la réponse d'authentification assemblée
     */
    public static AuthResponse of(
            String accessToken,
            String refreshToken,
            long   expiresInMs,
            UUID   userId,
            String username,
            String email,
            String role) {

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresInMs / 1000)
                .issuedAt(Instant.now())
                .userId(userId)
                .username(username)
                .email(email)
                .role(role)
                .build();
    }
}

