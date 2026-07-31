package wevioo.se.userms.Dto;

import java.util.UUID;

/**
 * Représentation interne d'un utilisateur, incluant son empreinte de mot de passe.
 *
 * <p><strong>Ce DTO ne doit jamais être exposé publiquement.</strong> Il n'est
 * renvoyé que par {@link wevioo.se.userms.Controller.InternalUserController},
 * protégé par le scope {@code user:credentials:read}, et sert au service
 * d'authentification à vérifier les identifiants fournis lors d'une connexion.</p>
 *
 * <p>Le rôle est transporté sous forme de chaîne plutôt que d'énumération, afin
 * que le service appelant n'ait pas besoin de partager la classe
 * {@link wevioo.se.userms.Entities.Role}.</p>
 *
 * @param id           identifiant technique du compte
 * @param username     nom de connexion
 * @param email        adresse e-mail, normalisée en minuscules
 * @param passwordHash empreinte du mot de passe, préfixée par l'algorithme utilisé
 * @param role         nom du rôle fonctionnel, par exemple {@code "ADMIN"}
 * @param active       {@code true} si le compte est actif ; un compte inactif
 *                     doit se voir refuser l'authentification
 */
public record UserCredentialsResponse(
        UUID id,
        String  username,
        String  email,
        String  passwordHash,
        String  role,
        boolean active
) {}
