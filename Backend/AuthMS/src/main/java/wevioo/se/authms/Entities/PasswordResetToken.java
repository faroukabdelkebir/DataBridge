package wevioo.se.authms.Entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Jeton à usage unique autorisant la réinitialisation d'un mot de passe.
 *
 * <p>Émis lors d'une demande « mot de passe oublié » et transmis à l'utilisateur par e-mail.
 * Un jeton n'est exploitable qu'une fois : il est marqué comme utilisé dès la réinitialisation
 * effectuée, et cesse d'être valable passée sa date d'expiration.</p>
 *
 * <p>La valeur stockée est le <em>haché</em> du jeton, jamais sa forme en clair : une fuite du
 * contenu de la table ne permet donc pas de réinitialiser un mot de passe.</p>
 *
 * @see wevioo.se.authms.Services.Utils.TokenHashUtil
 */
@Entity
@Table(name = "password_reset_tokens")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class PasswordResetToken {

    /** Identifiant technique du jeton, généré à la persistance. */
    @Id
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** Empreinte du jeton de réinitialisation ; unique en base. */
    @Column(nullable = false, unique = true, length = 256)
    private String token;

    /** Identifiant de l'utilisateur auquel le jeton se rapporte. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Instant au-delà duquel le jeton n'est plus valable. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Indique si le jeton a déjà servi à réinitialiser un mot de passe. */
    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;

    /** Date d'émission du jeton, renseignée automatiquement. */
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /** Constructeur sans argument requis par JPA pour instancier l'entité. */
    public PasswordResetToken() {
    }

    /**
     * Indique si le jeton a dépassé sa date d'expiration.
     *
     * <p>Ne tient pas compte du drapeau {@link #used} : un jeton peut être encore valide dans
     * le temps tout en ayant déjà été consommé.</p>
     *
     * @return {@code true} si l'instant courant est postérieur à la date d'expiration
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}