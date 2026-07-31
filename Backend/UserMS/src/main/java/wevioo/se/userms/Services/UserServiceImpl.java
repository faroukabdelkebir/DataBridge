package wevioo.se.userms.Services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wevioo.se.userms.Dto.*;
import wevioo.se.userms.Entities.Role;
import wevioo.se.userms.Entities.User;
import wevioo.se.userms.Exceptions.ConflictException;
import wevioo.se.userms.Exceptions.ResourceNotFoundException;
import wevioo.se.userms.Mapper.UserMapper;
import wevioo.se.userms.Repositories.UserRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Implémentation des règles métier de gestion des utilisateurs.
 *
 * <p>La classe est annotée {@code @Transactional(readOnly = true)} : les
 * lectures s'exécutent dans une transaction en lecture seule, et seules les
 * méthodes qui modifient des données redéfinissent ce réglage avec
 * {@code @Transactional}.</p>
 *
 * <p>Deux invariants sont maintenus ici plutôt que dans les contrôleurs :
 * l'adresse e-mail est toujours normalisée en minuscules avant comparaison ou
 * enregistrement, et un mot de passe reçu en clair est toujours encodé avant
 * d'atteindre la base.</p>
 *
 * <p>Les dépendances sont injectées par constructeur, généré par Lombok à partir
 * des champs {@code final}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    /** Accès à la table des utilisateurs. */
    private final UserRepository userRepository;

    /** Conversion des entités vers les DTO exposés. */
    private final UserMapper userMapper;

    /** Encodeur appliqué aux mots de passe reçus en clair. */
    private final PasswordEncoder passwordEncoder;


    /**
     * {@inheritDoc}
     *
     * <p>La disponibilité du nom de connexion est vérifiée avant celle de
     * l'e-mail, et l'encodage du mot de passe n'a lieu qu'une fois les deux
     * contrôles passés, afin de ne pas payer le coût de l'encodage pour une
     * requête vouée à échouer.</p>
     */
    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        log.debug("Creating user: username={}, role={}", request.username(), request.role());

        assertUsernameAvailable(request.username());
        assertEmailAvailable(request.email());

        User user = User.builder()
                .username(request.username())
                .email(request.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(request.role())
                .active(true)
                .build();

        User saved = userRepository.save(user);
        log.info("User created: id={}, username={}, role={}", saved.getId(), saved.getUsername(), saved.getRole());
        return userMapper.toResponse(saved);
    }


    /** {@inheritDoc} */
    @Override
    public UserResponse getUserById(UUID id) {
        return userMapper.toResponse(findUserById(id));
    }

    /** {@inheritDoc} */
    @Override
    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: username=" + username));
        return userMapper.toResponse(user);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Le filtrage et la pagination sont délégués à la base ; seule la page
     * retournée est convertie en résumés.</p>
     */
    @Override
    public Page<UserSummary> searchUsers(String search, Role role, Boolean active, Pageable pageable) {
        return userRepository.searchUsers(search, role, active, pageable)
                .map(userMapper::toSummary);
    }


    /**
     * {@inheritDoc}
     *
     * <p>La disponibilité de l'e-mail n'est revérifiée que s'il diffère
     * réellement de l'actuel, comparaison faite sans tenir compte de la casse :
     * renvoyer son propre e-mail ne doit pas provoquer de conflit.</p>
     */
    @Override
    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        log.debug("Updating user: id={}", id);

        User user = findUserById(id);

        if (request.email() != null && !request.email().equalsIgnoreCase(user.getEmail())) {
            assertEmailAvailable(request.email());
            user.setEmail(request.email().toLowerCase());
        }
        if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.active() != null) {
            user.setActive(request.active());
        }

        User updated = userRepository.save(user);
        log.info("User updated: id={}", updated.getId());
        return userMapper.toResponse(updated);
    }


    /**
     * {@inheritDoc}
     *
     * <p>Aucun enregistrement n'est effectué si le compte est déjà inactif, ce
     * qui évite de modifier inutilement l'horodatage de mise à jour.</p>
     */
    @Override
    @Transactional
    public void deactivateUser(UUID id) {
        User user = findUserById(id);
        if (!user.isActive()) {
            log.debug("User already inactive: id={}", id);
            return;
        }
        user.setActive(false);
        userRepository.save(user);
        log.info("User deactivated: id={}", id);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Aucun enregistrement n'est effectué si le compte est déjà actif, ce qui
     * évite de modifier inutilement l'horodatage de mise à jour.</p>
     */
    @Override
    @Transactional
    public void activateUser(UUID id) {
        User user = findUserById(id);
        if (user.isActive()) {
            log.debug("User already active: id={}", id);
            return;
        }
        user.setActive(true);
        userRepository.save(user);
        log.info("User activated: id={}", id);
    }


    /**
     * {@inheritDoc}
     *
     * <p>L'entité est d'abord chargée afin de lever une erreur explicite lorsque
     * le compte n'existe pas, plutôt que de supprimer silencieusement zéro ligne.</p>
     */
    @Override
    @Transactional
    public void deleteUser(UUID id) {
        User user = findUserById(id);
        userRepository.delete(user);
        log.info("User permanently deleted: id={}", id);
    }


    /**
     * {@inheritDoc}
     *
     * <p>Réalisée par une requête de mise à jour directe : l'entité n'est pas
     * chargée, et un identifiant inconnu n'affecte aucune ligne sans lever
     * d'erreur.</p>
     */
    @Override
    @Transactional
    public void recordLogin(UUID id) {
        userRepository.updateLastLoginAt(id, Instant.now());
    }


    /**
     * Charge un utilisateur ou échoue si l'identifiant est inconnu.
     *
     * <p>Point de passage unique des lectures par identifiant, garantissant un
     * message d'erreur homogène.</p>
     *
     * @param id identifiant recherché
     * @return l'entité correspondante, jamais {@code null}
     * @throws ResourceNotFoundException si aucun compte ne porte cet identifiant
     */
    private User findUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: id=" + id));
    }

    /**
     * Vérifie qu'un nom de connexion n'est pas déjà pris.
     *
     * @param username nom de connexion candidat
     * @throws ConflictException si un compte porte déjà ce nom
     */
    private void assertUsernameAvailable(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username already taken: " + username);
        }
    }

    /**
     * Vérifie qu'une adresse e-mail n'est pas déjà prise.
     *
     * <p>La comparaison est faite sur la forme minuscule, cohérente avec le
     * format de stockage.</p>
     *
     * @param email adresse e-mail candidate, dans une casse quelconque
     * @throws ConflictException si un compte porte déjà cette adresse
     */
    private void assertEmailAvailable(String email) {
        if (userRepository.existsByEmail(email.toLowerCase())) {
            throw new ConflictException("Email already registered: " + email);
        }
    }




    // --- Opérations internes réservées au service d'authentification ---


    /** {@inheritDoc} */
    @Override
    public Optional<UserCredentialsResponse> findCredentialsByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(u -> new UserCredentialsResponse(
                        u.getId(), u.getUsername(), u.getEmail(),
                        u.getPasswordHash(), u.getRole().name(), u.isActive()
                ));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<UserCredentialsResponse> findCredentialsByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(u -> new UserCredentialsResponse(
                        u.getId(), u.getUsername(), u.getEmail(),
                        u.getPasswordHash(), u.getRole().name(), u.isActive()
                ));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<UserCredentialsResponse> findCredentialsById(UUID id) {
        return userRepository.findById(id)
                .map(u -> new UserCredentialsResponse(
                        u.getId(), u.getUsername(), u.getEmail(),
                        u.getPasswordHash(), u.getRole().name(), u.isActive()
                ));
    }

    /**
     * {@inheritDoc}
     *
     * <p>L'empreinte reçue est stockée sans transformation :
     * {@link PasswordEncoder} n'est volontairement pas sollicité ici, sous peine
     * d'encoder une seconde fois une valeur déjà encodée.</p>
     */
    @Override
    @Transactional
    public void updatePassword(UUID id, String newHashedPassword) {
        User user = findUserById(id);
        user.setPasswordHash(newHashedPassword);
        userRepository.save(user);
        log.info("Password updated for user: id={}", id);
    }
}
