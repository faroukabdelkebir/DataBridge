package wevioo.se.userms.Entities;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Entité représentant un utilisateur de la plateforme, persistée dans la table
 * {@code platform_users}.
 *
 * <p>Le nom d'utilisateur et l'adresse e-mail sont soumis à une contrainte
 * d'unicité en base ; l'e-mail est systématiquement normalisé en minuscules par
 * la couche service avant enregistrement. Deux index secondaires accélèrent les
 * recherches filtrées par rôle et par statut d'activation.</p>
 *
 * <p>Les horodatages {@code createdAt} et {@code updatedAt} sont renseignés
 * automatiquement par l'audit JPA activé dans
 * {@link wevioo.se.userms.Config.JpaConfig} ; ils ne doivent pas être positionnés
 * manuellement.</p>
 *
 * <p>Le mot de passe n'est jamais stocké en clair : seul son hash est conservé,
 * et il est explicitement exclu de {@code toString()} pour éviter toute fuite
 * dans les journaux. L'égalité entre deux instances repose uniquement sur
 * l'identifiant.</p>
 *
 * <p>Cette entité ne quitte jamais la couche service : elle est convertie en DTO
 * par {@link wevioo.se.userms.Mapper.UserMapper} avant d'être exposée.</p>
 */
@Entity
@Table(
        name = "platform_users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_users_email",    columnNames = "email")
        },
        indexes = {
                @Index(name = "idx_users_role",     columnList = "role"),
                @Index(name = "idx_users_active",   columnList = "active"),
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "passwordHash")
@EqualsAndHashCode(of = "id")
public class User {

    /** Identifiant technique, généré par la base sous forme d'UUID et non modifiable. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Nom de connexion, unique sur la plateforme (60 caractères maximum). */
    @Column(name = "username", nullable = false, length = 60)
    private String username;

    /** Adresse e-mail, unique et stockée en minuscules (150 caractères maximum). */
    @Column(name = "email", nullable = false, length = 150)
    private String email;

    /**
     * Empreinte du mot de passe, préfixée par l'algorithme utilisé
     * (par exemple {@code {bcrypt}...}). Jamais exposée dans les réponses
     * publiques ni dans {@code toString()}.
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** Prénom de l'utilisateur, facultatif. */
    @Column(name = "first_name", length = 80)
    private String firstName;

    /** Nom de famille de l'utilisateur, facultatif. */
    @Column(name = "last_name", length = 80)
    private String lastName;

    /** Rôle fonctionnel, stocké sous forme de chaîne pour rester stable dans le temps. */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    /**
     * Indique si le compte est actif. La désactivation est privilégiée par
     * rapport à la suppression, car elle préserve l'historique.
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Date de création, renseignée une seule fois par l'audit JPA. */
    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    /** Date de dernière modification, mise à jour par l'audit JPA à chaque enregistrement. */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Date de la dernière connexion réussie, {@code null} tant que l'utilisateur
     * ne s'est jamais connecté.
     *
     * @see wevioo.se.userms.Services.UserService#recordLogin(UUID)
     */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
