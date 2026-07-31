package wevioo.se.authms.Repositories;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import wevioo.se.authms.Entities.PasswordResetToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Dépôt d'accès aux jetons de réinitialisation de mot de passe.
 *
 * @see PasswordResetToken
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /**
     * Recherche un jeton par son empreinte.
     *
     * <p>La valeur attendue est le haché du jeton, la forme en clair n'étant jamais stockée.</p>
     *
     * @param hashedToken empreinte du jeton recherché
     * @return le jeton correspondant, ou un {@link Optional} vide si aucun ne correspond
     */
    Optional<PasswordResetToken> findByToken(String hashedToken);

    /**
     * Marque comme utilisés tous les jetons encore actifs d'un utilisateur.
     *
     * <p>Invoqué lors de l'émission d'un nouveau jeton, afin qu'une seule demande de
     * réinitialisation reste exploitable à la fois.</p>
     *
     * @param userId identifiant de l'utilisateur concerné
     */
    @Modifying
    @Query("UPDATE PasswordResetToken p SET p.used = true WHERE p.userId = :userId AND p.used = false")
    void invalidateAllByUserId(@Param("userId") UUID userId);

    /**
     * Supprime définitivement les jetons expirés ou déjà consommés.
     *
     * <p>Destiné à la purge périodique de la table.</p>
     *
     * @param now instant de référence pour évaluer l'expiration
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken p WHERE p.expiresAt < :now OR p.used = true")
    void deleteExpiredAndUsed(@Param("now") Instant now);
}
