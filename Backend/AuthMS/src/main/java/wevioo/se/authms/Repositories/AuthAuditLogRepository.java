package wevioo.se.authms.Repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import wevioo.se.authms.Entities.AuthAuditLog;
import wevioo.se.authms.Entities.AuthEventType;

import java.time.Instant;
import java.util.UUID;

/**
 * Dépôt d'accès au journal d'audit des évènements d'authentification.
 *
 * <p>Fournit les recherches paginées exposées par l'API d'administration ainsi que le comptage
 * des échecs de connexion récents.</p>
 *
 * @see AuthAuditLog
 */
@Repository
public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog, UUID> {

    /**
     * Recherche les entrées d'audit relatives à un utilisateur.
     *
     * @param username nom d'utilisateur recherché
     * @param pageable pagination et tri
     * @return la page d'entrées correspondantes
     */
    Page<AuthAuditLog> findByUsername(String username, Pageable pageable);

    /**
     * Recherche les entrées d'audit d'un type d'évènement donné.
     *
     * @param eventType type d'évènement recherché
     * @param pageable  pagination et tri
     * @return la page d'entrées correspondantes
     */
    Page<AuthAuditLog> findByEventType(AuthEventType eventType, Pageable pageable);

    /**
     * Recherche les entrées d'audit d'un type d'évènement donné pour un utilisateur donné.
     *
     * @param username  nom d'utilisateur recherché
     * @param eventType type d'évènement recherché
     * @param pageable  pagination et tri
     * @return la page d'entrées correspondantes
     */
    Page<AuthAuditLog> findByUsernameAndEventType(String username, AuthEventType eventType, Pageable pageable);


    /**
     * Compte les échecs de connexion d'un utilisateur depuis un instant donné.
     *
     * <p>Sert à détecter les tentatives d'attaque par force brute sur un compte.</p>
     *
     * @param username nom d'utilisateur à examiner
     * @param since    borne temporelle basse, incluse
     * @return le nombre d'échecs de connexion enregistrés depuis cet instant
     */
    @Query("""
        SELECT COUNT(a) FROM AuthAuditLog a
        WHERE a.username  = :username
          AND a.eventType = 'LOGIN_FAILURE'
          AND a.success   = false
          AND a.createdAt >= :since
        """)
    long countFailedLoginAttempts(
            @Param("username") String username,
            @Param("since") Instant since
    );
}