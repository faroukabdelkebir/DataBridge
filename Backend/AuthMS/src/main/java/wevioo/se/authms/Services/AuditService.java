package wevioo.se.authms.Services;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import wevioo.se.authms.Entities.AuthAuditLog;
import wevioo.se.authms.Entities.AuthEventType;
import wevioo.se.authms.Repositories.AuthAuditLogRepository;

/**
 * Service d'écriture du journal d'audit des évènements d'authentification.
 *
 * <p>Les écritures sont asynchrones et effectuées dans une transaction distincte : la
 * journalisation ne ralentit pas la requête en cours et l'échec d'une opération métier
 * n'annule pas la trace de sa tentative — c'est précisément l'intérêt d'auditer les échecs.</p>
 *
 * @see AuthAuditLog
 */
@Service
public class AuditService {

    /** Dépôt de persistance des entrées d'audit. */
    private final AuthAuditLogRepository auditRepo;

    /**
     * Construit le service avec son dépôt d'audit.
     *
     * @param auditRepo dépôt de persistance des entrées d'audit
     */
    public AuditService(AuthAuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    /**
     * Enregistre un évènement d'authentification avec le contexte de la requête HTTP.
     *
     * <p>L'adresse IP et le user-agent sont extraits de la requête ; le détail et le
     * user-agent sont tronqués à 500 caractères pour tenir dans les colonnes.</p>
     *
     * @param eventType nature de l'évènement
     * @param username  nom d'utilisateur concerné, éventuellement {@code null}
     * @param success   issue de l'opération
     * @param detail    précision libre, typiquement le motif d'un échec
     * @param request   requête à l'origine de l'évènement
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuthEventType eventType,
                    String username,
                    boolean success,
                    String detail,
                    HttpServletRequest request) {

        AuthAuditLog entry = AuthAuditLog.builder()
                .eventType(eventType)
                .username(username)
                .ipAddress(extractIp(request))
                .userAgent(truncate(request.getHeader("User-Agent"), 500))
                .success(success)
                .detail(truncate(detail, 500))
                .build();

        auditRepo.save(entry);
    }

    /**
     * Enregistre un évènement d'authentification sans contexte de requête HTTP.
     *
     * <p>Destinée aux traitements hors requête (tâches planifiées, appels internes) ; l'adresse
     * IP et le user-agent restent alors vides.</p>
     *
     * @param eventType nature de l'évènement
     * @param username  nom d'utilisateur concerné, éventuellement {@code null}
     * @param success   issue de l'opération
     * @param detail    précision libre, tronquée à 500 caractères
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuthEventType eventType, String username, boolean success, String detail) {
        AuthAuditLog entry = AuthAuditLog.builder()
                .eventType(eventType)
                .username(username)
                .success(success)
                .detail(truncate(detail, 500))
                .build();
        auditRepo.save(entry);
    }


    /**
     * Détermine l'adresse IP du client à l'origine de la requête.
     *
     * <p>Privilégie la première adresse de l'en-tête {@code X-Forwarded-For}, seule à
     * identifier le client réel lorsque le service est placé derrière un répartiteur de charge
     * ou un reverse proxy ; à défaut, retourne l'adresse de la connexion.</p>
     *
     * @param request requête entrante
     * @return l'adresse IP retenue pour l'audit
     */
    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Tronque une chaîne à la longueur maximale admise par la colonne cible.
     *
     * @param value chaîne à tronquer, éventuellement {@code null}
     * @param max   longueur maximale conservée
     * @return la chaîne d'origine si elle tient dans la limite, sa troncature sinon,
     *         ou {@code null} si l'entrée est {@code null}
     */
    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}

