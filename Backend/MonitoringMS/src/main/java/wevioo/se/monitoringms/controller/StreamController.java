package wevioo.se.monitoringms.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import wevioo.se.monitoringms.service.SsePublisher;

/**
 * Endpoint SSE pour la mise à jour temps-réel du dashboard frontend.
 *
 * GET /api/v1/stream/jobs
 *
 * Événements émis :
 *   - "connected"  → confirmation de connexion avec clientId
 *   - "jobs"       → liste JSON des jobs en cours (toutes les 10 sec)
 *   - "alerts"     → alertes actives (toutes les 30 sec)
 *   - "heartbeat"  → keepalive (toutes les 25 sec)
 *
 * Utilisation côté JavaScript :
 * <code>
 *   const es = new EventSource('/api/v1/stream/jobs');
 *   es.addEventListener('jobs',    e => console.log(JSON.parse(e.data)));
 *   es.addEventListener('alerts',  e => console.log(JSON.parse(e.data)));
 *   es.addEventListener('heartbeat', () => {});
 * </code>
 */
@RestController
@RequestMapping("/api/v1/stream")
@RequiredArgsConstructor
@Tag(name = "Flux temps réel", description = "Flux SSE en temps réel pour le tableau de bord (traitements en cours et alertes)")
public class StreamController {

    /** Diffuseur du flux d'événements serveur. */
    private final SsePublisher ssePublisher;

    /**
     * Ouvre un flux d'événements serveur vers le client appelant.
     *
     * <p>La réponse est asynchrone : la requête HTTP reste ouverte et le
     * serveur y écrit au fil des diffusions. L'émetteur expire au bout de cinq
     * minutes, {@code EventSource} rétablissant alors la connexion de
     * lui-même.</p>
     *
     * @return l'émetteur associé au client, déjà enregistré auprès du diffuseur
     */
    @GetMapping(value = "/jobs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Flux SSE des jobs en cours",
            description = "Connexion persistante. Envoie les jobs actifs toutes les 10 secondes et les alertes toutes les 30 secondes.")
    public SseEmitter streamJobs() {
        return ssePublisher.subscribe();
    }
}
