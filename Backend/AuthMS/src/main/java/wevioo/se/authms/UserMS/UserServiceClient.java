package wevioo.se.authms.UserMS;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import wevioo.se.authms.Exceptions.UserServiceException;

import java.util.Optional;
import java.util.UUID;

/**
 * Client d'accès aux points d'entrée internes du microservice utilisateurs (UserMS).
 *
 * <p>Consulte les identifiants nécessaires à l'authentification et met à jour les mots de
 * passe. Les appels sont authentifiés automatiquement par le flow {@code client_credentials}
 * du client {@code auth-service}, seul habilité aux portées {@code user:credentials:*}.</p>
 *
 * <p>Toute défaillance de l'appel est convertie en {@link UserServiceException}, traduite en
 * {@code 503} par le gestionnaire global. Une réponse {@code 4xx} n'est en revanche pas une
 * défaillance : elle traduit l'absence de l'utilisateur recherché et se solde par un
 * {@link Optional} vide.</p>
 *
 * @see wevioo.se.authms.Config.InfraConfig
 */
@Slf4j
@Component
public class UserServiceClient {

    /** Client HTTP préconfiguré vers UserMS, porteur de l'authentification OAuth2. */
    private final RestClient restClient;

    /**
     * Construit le client avec le {@link RestClient} configuré pour UserMS.
     *
     * @param restClient client HTTP porteur de l'authentification OAuth2
     */
    public UserServiceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Identifiants d'un utilisateur tels que retournés par UserMS.
     *
     * @param id           identifiant unique de l'utilisateur
     * @param username     nom d'utilisateur
     * @param email        adresse e-mail
     * @param passwordHash empreinte du mot de passe, jamais sa forme en clair
     * @param role         rôle applicatif
     * @param active       indique si le compte est actif
     */
    public record UserCredentials(
            UUID    id,
            String  username,
            String  email,
            String  passwordHash,
            String  role,
            boolean active
    ) {}

    /**
     * Corps de la requête de mise à jour du mot de passe.
     *
     * @param passwordHash nouvelle empreinte du mot de passe, encodée par AuthMS avant l'envoi
     */
    public record PasswordUpdateRequest(String passwordHash) {}

    /**
     * Recherche les identifiants d'un utilisateur par son nom d'utilisateur.
     *
     * @param username nom d'utilisateur recherché
     * @return les identifiants trouvés, ou un {@link Optional} vide si l'utilisateur n'existe pas
     * @throws UserServiceException si UserMS est injoignable ou répond en erreur
     */
    public Optional<UserCredentials> findByUsername(String username) {
        try {
            UserCredentials creds = restClient.get()
                    .uri("/api/v1/users/internal/credentials?username={u}", username)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {})
                    .body(UserCredentials.class);
            return Optional.ofNullable(creds);
        } catch (Exception ex) {
            log.error("UserMS call failed [findByUsername={}]: {}", username, ex.getMessage());
            throw new UserServiceException("User service unavailable. Please try again later.");
        }
    }

    /**
     * Recherche les identifiants d'un utilisateur par son adresse e-mail.
     *
     * <p>Employée par le parcours « mot de passe oublié ».</p>
     *
     * @param email adresse e-mail recherchée
     * @return les identifiants trouvés, ou un {@link Optional} vide si l'utilisateur n'existe pas
     * @throws UserServiceException si UserMS est injoignable ou répond en erreur
     */
    public Optional<UserCredentials> findByEmail(String email) {
        try {
            UserCredentials creds = restClient.get()
                    .uri("/api/v1/users/internal/credentials?email={e}", email)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {})
                    .body(UserCredentials.class);
            return Optional.ofNullable(creds);
        } catch (Exception ex) {
            log.error("UserMS call failed [findByEmail={}]: {}", email, ex.getMessage());
            throw new UserServiceException("User service unavailable. Please try again later.");
        }
    }

    /**
     * Recherche les identifiants d'un utilisateur par son identifiant unique.
     *
     * @param userId identifiant recherché
     * @return les identifiants trouvés, ou un {@link Optional} vide si l'utilisateur n'existe pas
     * @throws UserServiceException si UserMS est injoignable ou répond en erreur
     */
    public Optional<UserCredentials> findById(UUID userId) {
        try {
            UserCredentials creds = restClient.get()
                    .uri("/api/v1/users/internal/credentials/{id}", userId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {})
                    .body(UserCredentials.class);
            return Optional.ofNullable(creds);
        } catch (Exception ex) {
            log.error("UserMS call failed [findById={}]: {}", userId, ex.getMessage());
            throw new UserServiceException("User service unavailable. Please try again later.");
        }
    }

    /**
     * Met à jour le mot de passe d'un utilisateur auprès de UserMS.
     *
     * <p>Le mot de passe est encodé par AuthMS avant l'appel : seule l'empreinte transite.</p>
     *
     * @param userId            identifiant de l'utilisateur concerné
     * @param newHashedPassword nouvelle empreinte du mot de passe
     * @throws UserServiceException si UserMS est injoignable ou répond en erreur
     */
    public void updatePassword(UUID userId, String newHashedPassword) {
        try {
            restClient.patch()
                    .uri("/api/v1/users/internal/{id}/password", userId)
                    .body(new PasswordUpdateRequest(newHashedPassword))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.error("UserMS updatePassword failed [userId={}]: {}", userId, ex.getMessage());
            throw new UserServiceException("User service unavailable. Please try again later.");
        }
    }
}

