package wevioo.se.authms.Entities;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Jeton de rafraîchissement matérialisant une session utilisateur persistante.
 *
 * <p>Émis à la connexion, il permet d'obtenir un nouveau jeton d'accès sans ressaisie des
 * identifiants. Contrairement au jeton d'accès, il est stocké côté serveur afin de pouvoir
 * être révoqué : c'est ce qui rend possible la déconnexion immédiate d'une session ou de
 * l'ensemble des appareils d'un utilisateur.</p>
 *
 * <p>La valeur stockée est le <em>haché</em> du jeton, jamais sa forme en clair.</p>
 *
 * @see wevioo.se.authms.Services.Utils.TokenHashUtil
 */
@Entity
@Table(name = "refresh_tokens")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class RefreshToken {

    /** Identifiant technique du jeton, généré à la persistance. */
    @Id
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** Empreinte du jeton de rafraîchissement ; unique en base. */
    @Column(nullable = false, unique = true, length = 512)
    private String token;

    /** Identifiant de l'utilisateur propriétaire de la session. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Instant au-delà duquel le jeton n'est plus valable. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Indique si le jeton a été révoqué avant son expiration, par exemple lors d'une déconnexion. */
    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    /** Date d'émission du jeton, renseignée automatiquement. */
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /** Constructeur sans argument requis par JPA pour instancier l'entité. */
    public RefreshToken() {
    }

    /**
     * Indique si le jeton a dépassé sa date d'expiration.
     *
     * <p>Ne tient pas compte du drapeau {@link #revoked} : un jeton révoqué reste inutilisable
     * même si sa date d'expiration n'est pas atteinte.</p>
     *
     * @return {@code true} si l'instant courant est postérieur à la date d'expiration
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}

