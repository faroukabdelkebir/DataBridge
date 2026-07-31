package wevioo.se.authms.Repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import wevioo.se.authms.Entities.RefreshToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Dépôt d'accès aux jetons de rafraîchissement.
 *
 * @see RefreshToken
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Recherche un jeton par son empreinte.
     *
     * <p>La valeur attendue est le haché du jeton, la forme en clair n'étant jamais stockée.</p>
     *
     * @param token empreinte du jeton recherché
     * @return le jeton correspondant, ou un {@link Optional} vide si aucun ne correspond
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Révoque tous les jetons de rafraîchissement d'un utilisateur.
     *
     * <p>Ferme ses sessions sur l'ensemble de ses appareils.</p>
     *
     * @param userId identifiant de l'utilisateur concerné
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.userId = :userId")
    void revokeAllByUserId(@Param("userId") UUID userId);

    /**
     * Supprime définitivement les jetons expirés ou révoqués.
     *
     * <p>Destiné à la purge périodique de la table.</p>
     *
     * @param now instant de référence pour évaluer l'expiration
     */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now OR r.revoked = true")
    void deleteExpiredAndRevoked(@Param("now") Instant now);

    /**
     * Vérifie qu'un jeton existe et n'a pas été révoqué.
     *
     * <p>Ne contrôle pas la date d'expiration : celle-ci doit être vérifiée séparément.</p>
     *
     * @param token empreinte du jeton recherché
     * @return {@code true} si un jeton correspondant existe et reste actif
     */
    boolean existsByTokenAndRevokedFalse(String token);


}
