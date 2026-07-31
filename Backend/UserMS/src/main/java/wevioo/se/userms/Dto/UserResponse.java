package wevioo.se.userms.Dto;

import lombok.Builder;
import wevioo.se.userms.Entities.Role;

import java.time.Instant;
import java.util.UUID;

/**
 * Représentation détaillée d'un utilisateur renvoyée par les endpoints publics.
 *
 * <p>C'est la vue complète d'un compte, utilisée en réponse à une création, à
 * une consultation par identifiant et à une mise à jour. Le hash du mot de passe
 * en est volontairement absent : seul {@link UserCredentialsResponse}, réservé
 * aux appels internes, l'expose.</p>
 *
 * @param id          identifiant technique du compte
 * @param username    nom de connexion
 * @param email       adresse e-mail, normalisée en minuscules
 * @param firstName   prénom, {@code null} si non renseigné
 * @param lastName    nom de famille, {@code null} si non renseigné
 * @param role        rôle fonctionnel attribué au compte
 * @param active      {@code true} si le compte est actif
 * @param createdAt   date de création du compte
 * @param updatedAt   date de dernière modification
 * @param lastLoginAt date de dernière connexion, {@code null} si l'utilisateur
 *                    ne s'est jamais connecté
 */
@Builder
public record UserResponse(
        UUID id,
        String  username,
        String  email,
        String  firstName,
        String  lastName,
        Role role,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt
) {}
