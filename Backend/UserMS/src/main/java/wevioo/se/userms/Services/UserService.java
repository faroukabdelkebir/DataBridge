package wevioo.se.userms.Services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import wevioo.se.userms.Dto.*;
import wevioo.se.userms.Entities.Role;

import java.util.Optional;
import java.util.UUID;

/**
 * Contrat métier de gestion des utilisateurs.
 *
 * <p>Cette interface constitue la frontière entre les contrôleurs et la
 * persistance : elle n'expose que des DTO, jamais l'entité
 * {@link wevioo.se.userms.Entities.User}. Elle regroupe deux familles
 * d'opérations :</p>
 * <ul>
 *   <li>les opérations publiques de gestion des comptes, appelées par
 *       {@link wevioo.se.userms.Controller.UserController} ;</li>
 *   <li>les opérations sur les identifiants, réservées au service
 *       d'authentification et appelées par
 *       {@link wevioo.se.userms.Controller.InternalUserController}.</li>
 * </ul>
 *
 * <p>Convention de gestion des absences : les méthodes publiques lèvent une
 * {@link wevioo.se.userms.Exceptions.ResourceNotFoundException} lorsque le
 * compte n'existe pas, tandis que les méthodes de consultation des identifiants
 * renvoient un {@link Optional} vide, laissant l'appelant décider du statut HTTP.</p>
 *
 * @see UserServiceImpl
 */
public interface UserService {

    /**
     * Crée un compte utilisateur.
     *
     * <p>Le nom de connexion et l'e-mail doivent être disponibles, l'e-mail est
     * normalisé en minuscules et le mot de passe encodé avant enregistrement. Le
     * compte est actif dès sa création.</p>
     *
     * @param request données du compte à créer, déjà validées
     * @return la vue complète du compte créé, identifiant et horodatages inclus
     * @throws wevioo.se.userms.Exceptions.ConflictException si le nom de
     *         connexion ou l'e-mail est déjà utilisé
     */
    UserResponse createUser(CreateUserRequest request);

    /**
     * Consulte un compte par son identifiant.
     *
     * @param id identifiant du compte
     * @return la vue complète du compte
     * @throws wevioo.se.userms.Exceptions.ResourceNotFoundException si aucun
     *         compte ne porte cet identifiant
     */
    UserResponse  getUserById(UUID id);

    /**
     * Consulte un compte par son nom de connexion.
     *
     * @param username nom de connexion, comparaison sensible à la casse
     * @return la vue complète du compte
     * @throws wevioo.se.userms.Exceptions.ResourceNotFoundException si aucun
     *         compte ne porte ce nom
     */
    UserResponse  getUserByUsername(String username);

    /**
     * Recherche paginée avec filtres facultatifs.
     *
     * @param search   terme libre recherché dans le nom de connexion ou l'e-mail,
     *                 {@code null} pour ne pas filtrer
     * @param role     rôle exact attendu, {@code null} pour ne pas filtrer
     * @param active   statut d'activation attendu, {@code null} pour ne pas filtrer
     * @param pageable pagination et tri à appliquer
     * @return la page de résumés correspondants, éventuellement vide
     */
    Page<UserSummary> searchUsers(String search, Role role, Boolean active, Pageable pageable);

    /**
     * Met à jour partiellement un compte.
     *
     * <p>Seuls les champs non {@code null} de la requête sont appliqués. Si
     * l'e-mail change, sa disponibilité est revérifiée ; si un mot de passe est
     * fourni, il est encodé avant enregistrement.</p>
     *
     * @param id      identifiant du compte à modifier
     * @param request champs à modifier
     * @return la vue complète du compte après modification
     * @throws wevioo.se.userms.Exceptions.ResourceNotFoundException si aucun
     *         compte ne porte cet identifiant
     * @throws wevioo.se.userms.Exceptions.ConflictException si le nouvel e-mail
     *         est déjà utilisé par un autre compte
     */
    UserResponse  updateUser(UUID id, UpdateUserRequest request);

    /**
     * Désactive un compte sans le supprimer.
     *
     * <p>Opération idempotente : désactiver un compte déjà inactif n'a aucun effet.</p>
     *
     * @param id identifiant du compte à désactiver
     * @throws wevioo.se.userms.Exceptions.ResourceNotFoundException si aucun
     *         compte ne porte cet identifiant
     */
    void          deactivateUser(UUID id);

    /**
     * Réactive un compte désactivé.
     *
     * <p>Opération idempotente : activer un compte déjà actif n'a aucun effet.</p>
     *
     * @param id identifiant du compte à réactiver
     * @throws wevioo.se.userms.Exceptions.ResourceNotFoundException si aucun
     *         compte ne porte cet identifiant
     */
    void          activateUser(UUID id);

    /**
     * Supprime définitivement un compte.
     *
     * <p>Suppression physique et irréversible. La désactivation via
     * {@link #deactivateUser(UUID)} est à privilégier lorsque l'historique doit
     * être conservé.</p>
     *
     * @param id identifiant du compte à supprimer
     * @throws wevioo.se.userms.Exceptions.ResourceNotFoundException si aucun
     *         compte ne porte cet identifiant
     */
    void          deleteUser(UUID id);

    /**
     * Enregistre l'instant d'une connexion réussie.
     *
     * <p>Appelée par le service d'authentification après vérification des
     * identifiants. Un identifiant inconnu est ignoré silencieusement.</p>
     *
     * @param id identifiant du compte concerné
     */
    void          recordLogin(UUID id);

    /**
     * Récupère les identifiants d'un compte à partir de son nom de connexion.
     *
     * <p>Usage interne uniquement : la réponse contient l'empreinte du mot de passe.</p>
     *
     * @param username nom de connexion recherché
     * @return les identifiants du compte, ou un {@link Optional} vide si le
     *         compte n'existe pas
     */
    Optional<UserCredentialsResponse> findCredentialsByUsername(String username);

    /**
     * Récupère les identifiants d'un compte à partir de son adresse e-mail.
     *
     * <p>Usage interne uniquement : la réponse contient l'empreinte du mot de passe.</p>
     *
     * @param email adresse e-mail recherchée, attendue en minuscules
     * @return les identifiants du compte, ou un {@link Optional} vide si le
     *         compte n'existe pas
     */
    Optional<UserCredentialsResponse> findCredentialsByEmail(String email);

    /**
     * Récupère les identifiants d'un compte à partir de son identifiant technique.
     *
     * <p>Usage interne uniquement : la réponse contient l'empreinte du mot de passe.</p>
     *
     * @param id identifiant du compte
     * @return les identifiants du compte, ou un {@link Optional} vide si le
     *         compte n'existe pas
     */
    Optional<UserCredentialsResponse> findCredentialsById(UUID id);

    /**
     * Remplace l'empreinte du mot de passe d'un compte.
     *
     * <p>Usage interne uniquement. Contrairement à
     * {@link #updateUser(UUID, UpdateUserRequest)}, la valeur reçue est stockée
     * telle quelle : l'encodage incombe au service d'authentification appelant.</p>
     *
     * @param id                identifiant du compte concerné
     * @param newHashedPassword empreinte déjà calculée, préfixée par l'algorithme utilisé
     * @throws wevioo.se.userms.Exceptions.ResourceNotFoundException si aucun
     *         compte ne porte cet identifiant
     */
    void updatePassword(UUID id, String newHashedPassword);
}

