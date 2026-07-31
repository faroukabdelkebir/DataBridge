package wevioo.se.authms.Entities;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Entrée du journal d'audit des évènements d'authentification.
 *
 * <p>Chaque tentative sensible — connexion, déconnexion, rafraîchissement de jeton, opération
 * sur un mot de passe — donne lieu à un enregistrement, qu'elle ait abouti ou non. Ces traces
 * servent au suivi de sécurité et à la détection d'activités suspectes.</p>
 *
 * <p>La table est indexée sur le nom d'utilisateur, le type d'évènement et la date de création,
 * les trois axes de filtrage exposés par {@code GET /api/v1/auth/audit}. L'horodatage est
 * renseigné automatiquement par l'audit JPA.</p>
 *
 * @see AuthEventType
 * @see wevioo.se.authms.Services.AuditService
 */
@Entity
@Table(name = "auth_audit_log",
        indexes = {
                @Index(name = "idx_aal_username",   columnList = "username"),
                @Index(name = "idx_aal_event_type", columnList = "event_type"),
                @Index(name = "idx_aal_created_at", columnList = "created_at")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@AllArgsConstructor
@Builder
public class AuthAuditLog {

    /** Identifiant technique de l'entrée, généré par la base. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    /** Nature de l'évènement journalisé, stockée sous forme de libellé. */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private AuthEventType eventType;

    /** Nom d'utilisateur concerné ; peut être absent si l'évènement n'est rattaché à aucun compte. */
    @Column(length = 100)
    private String username;

    /** Adresse IP d'origine de la requête ; la longueur permet de stocker une adresse IPv6. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /** En-tête {@code User-Agent} de la requête, tronqué à 500 caractères. */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** Indique si l'opération a abouti. */
    @Column(nullable = false)
    private boolean success;

    /** Précision libre sur l'évènement, typiquement le motif d'un échec. */
    @Column(length = 500)
    private String detail;

    /** Date de création de l'entrée, renseignée automatiquement et non modifiable. */
    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    /** Constructeur sans argument requis par JPA pour instancier l'entité. */
    public AuthAuditLog() {
    }


}
