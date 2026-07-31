package wevioo.se.userms.Repositories;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import wevioo.se.userms.Entities.Role;
import wevioo.se.userms.Entities.User;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Accès aux données des utilisateurs.
 *
 * <p>Hérite de {@link JpaRepository} pour les opérations CRUD standard et la
 * pagination. Les méthodes dérivées du nom sont générées par Spring Data ; les
 * requêtes plus élaborées sont écrites en JPQL.</p>
 *
 * <p>Les recherches par e-mail supposent une valeur en minuscules : la
 * normalisation est de la responsabilité de la couche service.</p>
 *
 * @see wevioo.se.userms.Services.UserServiceImpl
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Recherche un utilisateur par son nom de connexion.
     *
     * @param username nom de connexion recherché, comparaison sensible à la casse
     * @return l'utilisateur trouvé, ou un {@code Optional} vide
     */
    Optional<User> findByUsername(String username);

    /**
     * Recherche un utilisateur par son adresse e-mail.
     *
     * @param email adresse e-mail recherchée, attendue en minuscules
     * @return l'utilisateur trouvé, ou un {@code Optional} vide
     */
    Optional<User> findByEmail(String email);

    /**
     * Vérifie si un nom de connexion est déjà utilisé.
     *
     * @param username nom de connexion à tester
     * @return {@code true} si un compte porte déjà ce nom
     */
    boolean existsByUsername(String username);

    /**
     * Vérifie si une adresse e-mail est déjà utilisée.
     *
     * @param email adresse e-mail à tester, attendue en minuscules
     * @return {@code true} si un compte porte déjà cette adresse
     */
    boolean existsByEmail(String email);

    /**
     * Recherche multicritère paginée.
     *
     * <p>Chaque critère est facultatif : un paramètre {@code null} désactive le
     * filtre correspondant, ce qui permet de couvrir toutes les combinaisons avec
     * une seule requête. Le terme libre est comparé en minuscules au nom de
     * connexion et à l'e-mail, avec une correspondance partielle.</p>
     *
     * @param search   terme libre recherché dans le nom de connexion ou l'e-mail,
     *                 {@code null} pour ne pas filtrer
     * @param role     rôle exact attendu, {@code null} pour ne pas filtrer
     * @param active   statut d'activation attendu, {@code null} pour ne pas filtrer
     * @param pageable pagination et tri à appliquer
     * @return la page d'utilisateurs satisfaisant tous les critères fournis
     */
    @Query("""
        SELECT u FROM User u
        WHERE (:search IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
                               OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:role   IS NULL OR u.role   = :role)
          AND (:active IS NULL OR u.active = :active)
        """)
    Page<User> searchUsers(
            @Param("search") String search,
            @Param("role")   Role role,
            @Param("active") Boolean active,
            Pageable pageable
    );


    /**
     * Met à jour la date de dernière connexion sans charger l'entité.
     *
     * <p>Requête de modification directe, choisie pour éviter un aller-retour de
     * lecture et ne pas déclencher l'audit sur {@code updatedAt} : une connexion
     * ne constitue pas une modification du profil.</p>
     *
     * <p>Aucune erreur n'est levée si l'identifiant n'existe pas ; la requête
     * n'affecte simplement aucune ligne. Le contexte de persistance n'étant pas
     * synchronisé automatiquement, une entité déjà chargée dans la même
     * transaction conservera l'ancienne valeur.</p>
     *
     * @param id        identifiant de l'utilisateur concerné
     * @param loginTime horodatage de la connexion à enregistrer
     */
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loginTime WHERE u.id = :id")
    void updateLastLoginAt(@Param("id") UUID id, @Param("loginTime") Instant loginTime);
}

