package wevioo.se.authms.Entities;

/**
 * Types d'évènements consignés dans le journal d'audit d'authentification.
 *
 * <p>Les libellés sont persistés tels quels en base : les renommer romprait la lecture des
 * enregistrements existants.</p>
 *
 * @see AuthAuditLog
 */
public enum AuthEventType {

    /** Connexion réussie. */
    LOGIN_SUCCESS,

    /** Tentative de connexion rejetée (identifiants invalides ou compte inaccessible). */
    LOGIN_FAILURE,

    /** Déconnexion, sur une session ou sur l'ensemble des appareils. */
    LOGOUT,

    /** Jeton d'accès renouvelé avec succès à partir d'un jeton de rafraîchissement. */
    TOKEN_REFRESH,

    /** Rafraîchissement refusé : jeton inconnu, expiré ou révoqué. */
    TOKEN_REFRESH_FAILURE,

    /** Demande de réinitialisation de mot de passe enregistrée. */
    PASSWORD_RESET_REQUEST,

    /** Mot de passe réinitialisé avec succès via un jeton valide. */
    PASSWORD_RESET_SUCCESS,

    /** Réinitialisation refusée : jeton invalide, expiré ou déjà utilisé. */
    PASSWORD_RESET_FAILURE,

    /** Mot de passe modifié par un utilisateur authentifié. */
    PASSWORD_CHANGE
}
