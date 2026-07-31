package wevioo.se.authms.Services;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wevioo.se.authms.Entities.AuthAuditLog;
import wevioo.se.authms.Entities.AuthEventType;
import wevioo.se.authms.Repositories.AuthAuditLogRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link AuditService}.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    /** Dépôt simulé, dont on capture les entrées persistées. */
    @Mock
    private AuthAuditLogRepository auditRepo;

    /** Requête HTTP simulée, source de l'adresse IP et du user-agent. */
    @Mock
    private HttpServletRequest request;

    /** Service testé. */
    @InjectMocks
    private AuditService auditService;

    /** Constructeur par défaut, utilisé par JUnit pour instancier la classe de test. */
    AuditServiceTest() {
    }

    /**
     * Vérifie que l'adresse IP retenue est la première de l'en-tête {@code X-Forwarded-For}
     * et que le user-agent comme le détail sont tronqués à 500 caractères.
     */
    @Test
    void logWithRequestUsesForwardedIpAndTruncatesLongValues() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");
        when(request.getHeader("User-Agent")).thenReturn("a".repeat(600));

        auditService.log(AuthEventType.LOGIN_FAILURE, "alice", false, "d".repeat(600), request);

        ArgumentCaptor<AuthAuditLog> captor = ArgumentCaptor.forClass(AuthAuditLog.class);
        verify(auditRepo).save(captor.capture());
        AuthAuditLog saved = captor.getValue();

        assertThat(saved.getEventType()).isEqualTo(AuthEventType.LOGIN_FAILURE);
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(saved.getUserAgent()).hasSize(500);
        assertThat(saved.getDetail()).hasSize(500);
        assertThat(saved.isSuccess()).isFalse();
    }

    /**
     * Vérifie que la variante sans requête HTTP persiste bien l'évènement, réduit à ses
     * attributs essentiels.
     */
    @Test
    void logWithoutRequestPersistsBasicAuditEntry() {
        auditService.log(AuthEventType.LOGOUT, "alice", true, "ok");

        ArgumentCaptor<AuthAuditLog> captor = ArgumentCaptor.forClass(AuthAuditLog.class);
        verify(auditRepo).save(captor.capture());

        assertThat(captor.getValue().getEventType()).isEqualTo(AuthEventType.LOGOUT);
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
        assertThat(captor.getValue().isSuccess()).isTrue();
        assertThat(captor.getValue().getDetail()).isEqualTo("ok");
    }
}
