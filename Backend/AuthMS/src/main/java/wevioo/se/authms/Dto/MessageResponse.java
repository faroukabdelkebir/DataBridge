package wevioo.se.authms.Dto;

import lombok.Builder;

import java.time.Instant;

/**
 * Réponse générique porteuse d'un message et de son horodatage.
 *
 * <p>Employée par les opérations qui n'ont pas de données à retourner (déconnexion,
 * réinitialisation ou changement de mot de passe) pour confirmer leur bon déroulement.</p>
 *
 * @param message   texte destiné au client
 * @param timestamp instant de production du message
 */
@Builder
public record MessageResponse(
        String  message,
        Instant timestamp
) {
    /**
     * Fabrique une réponse horodatée à l'instant courant.
     *
     * @param message texte destiné au client
     * @return la réponse assemblée
     */
    public static MessageResponse of(String message) {
        return new MessageResponse(message, Instant.now());
    }
}
