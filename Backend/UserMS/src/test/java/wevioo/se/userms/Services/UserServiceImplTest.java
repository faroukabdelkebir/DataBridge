package wevioo.se.userms.Services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import wevioo.se.userms.Dto.CreateUserRequest;
import wevioo.se.userms.Dto.UpdateUserRequest;
import wevioo.se.userms.Dto.UserResponse;
import wevioo.se.userms.Dto.UserSummary;
import wevioo.se.userms.Entities.Role;
import wevioo.se.userms.Entities.User;
import wevioo.se.userms.Exceptions.ConflictException;
import wevioo.se.userms.Exceptions.ResourceNotFoundException;
import wevioo.se.userms.Mapper.UserMapper;
import wevioo.se.userms.Repositories.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires des règles métier de {@link UserServiceImpl}.
 *
 * <p>Le dépôt, le mapper et l'encodeur sont simulés : aucune base de données ni
 * contexte Spring n'est nécessaire, ce qui permet de cibler précisément les
 * règles portées par le service — normalisation de l'e-mail, encodage du mot de
 * passe, contrôles d'unicité, sémantique partielle de la mise à jour et
 * idempotence de l'activation ou de la désactivation.</p>
 *
 * <p>Plusieurs cas vérifient aussi ce qui <em>ne doit pas</em> se produire, via
 * {@code never()} et {@code verifyNoInteractions()} : ne pas encoder un mot de
 * passe pour une requête vouée à échouer, ou ne pas enregistrer une entité déjà
 * dans l'état voulu.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    /** Dépôt simulé, seul point d'accès aux données du service. */
    @Mock
    private UserRepository userRepository;

    /** Mapper simulé : les tests vérifient qu'il est appelé, pas ce qu'il produit. */
    @Mock
    private UserMapper userMapper;

    /** Encodeur simulé, renvoyant une empreinte prévisible. */
    @Mock
    private PasswordEncoder passwordEncoder;

    /** Service testé, avec les mocks injectés par constructeur. */
    @InjectMocks
    private UserServiceImpl userService;

    /**
     * Vérifie qu'à la création, l'e-mail est mis en minuscules, le mot de passe
     * encodé et le compte marqué actif. L'entité réellement transmise au dépôt
     * est capturée pour être inspectée champ par champ.
     */
    @Test
    void createUserPersistsActiveUserWithLowercaseEmailAndEncodedPassword() {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("john")
                .email("John.Doe@Example.COM")
                .password("Password1!")
                .firstName("John")
                .lastName("Doe")
                .role(Role.ADMIN)
                .build();
        UUID id = UUID.randomUUID();
        User saved = user("john", "john.doe@example.com", Role.ADMIN, true);
        saved.setId(id);
        UserResponse expected = response(id, "john", "john.doe@example.com", Role.ADMIN, true);

        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(userRepository.existsByEmail("john.doe@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1!")).thenReturn("{bcrypt}hash");
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(userMapper.toResponse(saved)).thenReturn(expected);

        UserResponse actual = userService.createUser(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User persisted = userCaptor.getValue();
        assertThat(persisted.getUsername()).isEqualTo("john");
        assertThat(persisted.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(persisted.getPasswordHash()).isEqualTo("{bcrypt}hash");
        assertThat(persisted.isActive()).isTrue();
        assertThat(actual).isEqualTo(expected);
    }

    /**
     * Vérifie qu'un nom de connexion déjà pris interrompt la création
     * immédiatement : ni contrôle de l'e-mail, ni encodage du mot de passe, ni
     * enregistrement ne doivent avoir lieu.
     */
    @Test
    void createUserRejectsDuplicateUsernameBeforeEncodingPassword() {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("john")
                .email("john@example.com")
                .password("Password1!")
                .role(Role.VISITOR)
                .build();

        when(userRepository.existsByUsername("john")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Username already taken");
        verify(userRepository, never()).existsByEmail(any());
        verifyNoInteractions(passwordEncoder, userMapper);
        verify(userRepository, never()).save(any());
    }

    /**
     * Vérifie la sémantique de la mise à jour partielle : les champs absents de
     * la requête restent inchangés, le nouvel e-mail est contrôlé puis normalisé,
     * et l'encodeur n'est pas sollicité en l'absence de mot de passe.
     */
    @Test
    void updateUserOnlyAppliesProvidedFieldsAndRechecksChangedEmail() {
        UUID id = UUID.randomUUID();
        User existing = user("john", "john@example.com", Role.VISITOR, true);
        existing.setId(id);
        UpdateUserRequest request = UpdateUserRequest.builder()
                .email("New.Email@Example.COM")
                .firstName("Jane")
                .active(false)
                .build();
        UserResponse expected = response(id, "john", "new.email@example.com", Role.VISITOR, false);

        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmail("new.email@example.com")).thenReturn(false);
        when(userRepository.save(existing)).thenReturn(existing);
        when(userMapper.toResponse(existing)).thenReturn(expected);

        UserResponse actual = userService.updateUser(id, request);

        assertThat(existing.getEmail()).isEqualTo("new.email@example.com");
        assertThat(existing.getFirstName()).isEqualTo("Jane");
        assertThat(existing.getLastName()).isEqualTo("Doe");
        assertThat(existing.getRole()).isEqualTo(Role.VISITOR);
        assertThat(existing.isActive()).isFalse();
        assertThat(actual).isEqualTo(expected);
        verifyNoInteractions(passwordEncoder);
    }

    /**
     * Vérifie l'idempotence de la désactivation : un compte déjà inactif n'est
     * pas réenregistré, ce qui laisse son horodatage de mise à jour intact.
     */
    @Test
    void deactivateUserDoesNotSaveAlreadyInactiveUser() {
        UUID id = UUID.randomUUID();
        User inactive = user("john", "john@example.com", Role.VISITOR, false);
        inactive.setId(id);

        when(userRepository.findById(id)).thenReturn(Optional.of(inactive));

        userService.deactivateUser(id);

        verify(userRepository, never()).save(any());
    }

    /** Vérifie qu'un compte inactif est bien basculé en actif puis enregistré. */
    @Test
    void activateUserSavesInactiveUserAsActive() {
        UUID id = UUID.randomUUID();
        User inactive = user("john", "john@example.com", Role.VISITOR, false);
        inactive.setId(id);

        when(userRepository.findById(id)).thenReturn(Optional.of(inactive));

        userService.activateUser(id);

        assertThat(inactive.isActive()).isTrue();
        verify(userRepository).save(inactive);
    }

    /**
     * Vérifie qu'un identifiant inconnu déclenche une
     * {@link ResourceNotFoundException} dont le message mentionne l'identifiant
     * recherché.
     */
    @Test
    void getUserByIdThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    /**
     * Vérifie que la page d'entités renvoyée par le dépôt est convertie en page
     * de résumés, pagination préservée.
     */
    @Test
    void searchUsersMapsRepositoryPageToSummaries() {
        User user = user("john", "john@example.com", Role.ADMIN, true);
        UserSummary summary = UserSummary.builder()
                .id(UUID.randomUUID())
                .username("john")
                .email("john@example.com")
                .role(Role.ADMIN)
                .active(true)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        PageRequest pageable = PageRequest.of(0, 20);

        when(userRepository.searchUsers("jo", Role.ADMIN, true, pageable))
                .thenReturn(new PageImpl<>(List.of(user), pageable, 1));
        when(userMapper.toSummary(user)).thenReturn(summary);

        assertThat(userService.searchUsers("jo", Role.ADMIN, true, pageable).getContent())
                .containsExactly(summary);
    }

    /**
     * Vérifie que l'empreinte fournie par le service d'authentification est
     * stockée telle quelle, sans second encodage.
     */
    @Test
    void updatePasswordStoresHashProvidedByAuthService() {
        UUID id = UUID.randomUUID();
        User user = user("john", "john@example.com", Role.ADMIN, true);
        user.setId(id);

        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.updatePassword(id, "{bcrypt}new-hash");

        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}new-hash");
        verify(userRepository).save(user);
        verifyNoInteractions(passwordEncoder, userMapper);
    }

    /**
     * Fabrique une entité utilisateur pour les besoins des tests.
     *
     * @param username nom de connexion
     * @param email    adresse e-mail
     * @param role     rôle attribué
     * @param active   statut d'activation
     * @return l'entité construite, sans identifiant et avec des horodatages fixes
     */
    private User user(String username, String email, Role role, boolean active) {
        return User.builder()
                .username(username)
                .email(email)
                .passwordHash("{bcrypt}hash")
                .firstName("John")
                .lastName("Doe")
                .role(role)
                .active(active)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-02T00:00:00Z"))
                .build();
    }

    /**
     * Fabrique la réponse attendue correspondant à une entité de test.
     *
     * @param id       identifiant du compte
     * @param username nom de connexion
     * @param email    adresse e-mail
     * @param role     rôle attribué
     * @param active   statut d'activation
     * @return la réponse construite, avec des horodatages fixes
     */
    private UserResponse response(UUID id, String username, String email, Role role, boolean active) {
        return UserResponse.builder()
                .id(id)
                .username(username)
                .email(email)
                .firstName("John")
                .lastName("Doe")
                .role(role)
                .active(active)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-02T00:00:00Z"))
                .build();
    }
}
