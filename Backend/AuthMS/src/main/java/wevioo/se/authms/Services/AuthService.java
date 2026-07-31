package wevioo.se.authms.Services;

import jakarta.servlet.http.HttpServletRequest;
import wevioo.se.authms.Dto.*;

import java.util.UUID;

/**
 * Contrat de la logique métier d'authentification.
 *
 * <p>Couvre le cycle de vie des sessions et la gestion des mots de passe. Chaque opération
 * reçoit la requête HTTP entrante afin d'alimenter le journal d'audit en adresse IP et
 * user-agent.</p>
 *
 * @see AuthServiceImpl
 */
public interface AuthService {

    /**
     * Authentifie un utilisateur et ouvre une session.
     *
     * @param request     identifiants de connexion
     * @param httpRequest requête HTTP entrante, utilisée pour l'audit
     * @return les jetons émis et le profil de l'utilisateur
     */
    AuthResponse login(LoginRequest request, HttpServletRequest httpRequest);

    /**
     * Ferme une session en révoquant le jeton de rafraîchissement fourni.
     *
     * @param refreshToken jeton de rafraîchissement à révoquer
     * @param httpRequest  requête HTTP entrante, utilisée pour l'audit
     * @return un message de confirmation
     */
    MessageResponse logout(String refreshToken, HttpServletRequest httpRequest);

    /**
     * Ferme toutes les sessions d'un utilisateur en révoquant l'ensemble de ses jetons.
     *
     * @param userId      identifiant de l'utilisateur concerné
     * @param httpRequest requête HTTP entrante, utilisée pour l'audit
     * @return un message de confirmation
     */
    MessageResponse logoutAll(UUID userId, HttpServletRequest httpRequest);

    /**
     * Émet un nouveau couple de jetons à partir d'un jeton de rafraîchissement valide.
     *
     * @param request     jeton de rafraîchissement à échanger
     * @param httpRequest requête HTTP entrante, utilisée pour l'audit
     * @return les nouveaux jetons et le profil de l'utilisateur
     */
    AuthResponse refresh(RefreshTokenRequest request, HttpServletRequest httpRequest);

    /**
     * Déclenche l'envoi d'un lien de réinitialisation de mot de passe.
     *
     * @param request     adresse e-mail du compte concerné
     * @param httpRequest requête HTTP entrante, utilisée pour l'audit
     * @return un message de confirmation, identique que le compte existe ou non
     */
    MessageResponse forgotPassword(ForgotPasswordRequest request, HttpServletRequest httpRequest);

    /**
     * Réinitialise un mot de passe à partir d'un jeton reçu par e-mail.
     *
     * @param request     jeton de réinitialisation et nouveau mot de passe
     * @param httpRequest requête HTTP entrante, utilisée pour l'audit
     * @return un message de confirmation
     */
    MessageResponse resetPassword(ResetPasswordRequest request, HttpServletRequest httpRequest);

    /**
     * Change le mot de passe d'un utilisateur authentifié, après vérification de l'ancien.
     *
     * @param userId      identifiant de l'utilisateur concerné
     * @param request     mot de passe actuel et nouveau mot de passe
     * @param httpRequest requête HTTP entrante, utilisée pour l'audit
     * @return un message de confirmation
     */
    MessageResponse changePassword(UUID userId, ChangePasswordRequest request, HttpServletRequest httpRequest);
}

