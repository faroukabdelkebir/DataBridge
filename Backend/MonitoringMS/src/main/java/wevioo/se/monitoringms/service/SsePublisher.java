package wevioo.se.monitoringms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import wevioo.se.monitoringms.dto.JobExecutionDto;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Diffusion temps réel vers le tableau de bord, par flux d'événements
 * serveur (Server-Sent Events).
 *
 * <p>Exposé par {@code GET /api/v1/stream/jobs} et consommé par l'API
 * {@code EventSource} native des navigateurs.</p>
 *
 * <p>Événements émis :</p>
 * <ul>
 *   <li>{@code connected} — confirmation d'abonnement, à la connexion ;</li>
 *   <li>{@code jobs} — traitements en cours, toutes les dix secondes ;</li>
 *   <li>{@code alerts} — alertes actives, toutes les trente secondes ;</li>
 *   <li>{@code heartbeat} — maintien de connexion, toutes les vingt-cinq secondes.</li>
 * </ul>
 *
 * <p>Le registre des clients est purgé de deux façons : par les rappels de
 * fin, d'expiration et d'erreur de chaque émetteur, et par la détection d'une
 * écriture en échec lors d'une diffusion.</p>
 */
@Service
@Slf4j
public class SsePublisher {

    /** Source des traitements en cours diffusés périodiquement. */
    private final DashboardService dashboardService;

    /** Source des alertes actives diffusées périodiquement. */
    private final AlertService     alertService;

    /** Sérialiseur JSON des charges utiles émises. */
    private final ObjectMapper objectMapper;

    /**
     * Clients abonnés, indexés par identifiant de connexion. La carte est
     * concurrente : les diffusions planifiées et les abonnements HTTP y
     * accèdent simultanément.
     */
    private final Map<Long, SseEmitter> clients = new ConcurrentHashMap<>();

    /** Générateur d'identifiants de connexion. */
    private final AtomicLong idSeq   = new AtomicLong();

    /**
     * Construit le diffuseur.
     *
     * @param objectMapper     sérialiseur JSON configuré par Spring Boot
     * @param dashboardService source des traitements en cours
     * @param alertService     source des alertes actives
     */
    public SsePublisher(ObjectMapper objectMapper, DashboardService dashboardService, AlertService alertService) {
        this.objectMapper     = objectMapper;
        this.dashboardService = dashboardService;
        this.alertService     = alertService;
    }

    // ── Abonnement ───────────────────────────────────────────────────────────

    /**
     * Abonne un nouveau client au flux.
     *
     * <p>L'émetteur expire au bout de cinq minutes : {@code EventSource}
     * rétablit alors la connexion de lui-même, ce qui évite de conserver
     * indéfiniment des connexions oubliées.</p>
     *
     * <p>Un événement {@code connected} est émis immédiatement ; si son envoi
     * échoue, le client est retiré sans que l'appel échoue pour autant.</p>
     *
     * @return l'émetteur à retourner au client HTTP
     */
    public SseEmitter subscribe() {
        long clientId    = idSeq.incrementAndGet();
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        clients.put(clientId, emitter);
        log.info("[SSE] Nouveau client n°{} (total : {})", clientId, clients.size());

        emitter.onCompletion(() -> {
            clients.remove(clientId);
            log.debug("[SSE] Client n°{} déconnecté", clientId);
        });
        emitter.onTimeout(() -> {
            clients.remove(clientId);
            log.debug("[SSE] Délai d'attente dépassé pour le client n°{}", clientId);
        });
        emitter.onError(e -> {
            clients.remove(clientId);
            log.debug("[SSE] Erreur du client n°{} : {}", clientId, e.getMessage());
        });

        // Envoi immédiat du statut initial
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"clientId\":" + clientId + ",\"time\":\"" + LocalDateTime.now() + "\"}"));
        } catch (IOException e) {
            clients.remove(clientId);
        }

        return emitter;
    }

    // ── Broadcast périodique ─────────────────────────────────────────────────

    /**
     * Diffuse les traitements en cours, toutes les dix secondes.
     *
     * <p>Aucune requête n'est émise en l'absence de client. Toute erreur est
     * absorbée : une indisponibilité passagère de la base ne doit pas
     * interrompre la planification.</p>
     */
    @Scheduled(fixedDelay = 10_000)
    public void broadcastLiveJobs() {
        if (clients.isEmpty()) return;
        try {
            List<JobExecutionDto> liveJobs = dashboardService.getLiveJobs();
            broadcast("jobs", liveJobs);
        } catch (Exception e) {
            log.debug("[SSE] Erreur de diffusion des traitements en cours : {}", e.getMessage());
        }
    }

    /**
     * Diffuse les alertes actives, toutes les trente secondes.
     *
     * <p>Comme pour les traitements, l'absence de client court-circuite le
     * traitement et les erreurs sont absorbées.</p>
     */
    @Scheduled(fixedDelay = 30_000)
    public void broadcastAlerts() {
        if (clients.isEmpty()) return;
        try {
            broadcast("alerts", alertService.getActiveAlerts());
        } catch (Exception e) {
            log.debug("[SSE] Erreur de diffusion des alertes : {}", e.getMessage());
        }
    }

    /**
     * Émet un battement de cœur, toutes les vingt-cinq secondes.
     *
     * <p>Maintient la connexion ouverte : sans trafic, les mandataires
     * intermédiaires la fermeraient d'eux-mêmes.</p>
     */
    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        if (clients.isEmpty()) return;
        broadcast("heartbeat", Map.of("time", LocalDateTime.now().toString()));
    }

    // ── Broadcast ponctuel (appelé par d'autres services) ───────────────────

    /**
     * Diffuse un événement ponctuel, hors cadence planifiée.
     *
     * <p>Destiné aux autres services souhaitant notifier le tableau de bord
     * sans attendre la prochaine diffusion.</p>
     *
     * @param eventType nom de l'événement, tel qu'écouté côté client
     * @param data      charge utile, sérialisée en JSON
     */
    public void publishJobEvent(String eventType, Object data) {
        broadcast(eventType, data);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Émet un événement vers tous les clients abonnés.
     *
     * <p>Un échec de sérialisation annule la diffusion sans lever
     * d'exception. Les clients dont l'écriture échoue sont réputés déconnectés
     * et retirés du registre.</p>
     *
     * @param eventName nom de l'événement
     * @param data      charge utile, sérialisée en JSON
     */
    private void broadcast(String eventName, Object data) {
        if (clients.isEmpty()) return;
        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("[SSE] Erreur de sérialisation : {}", e.getMessage());
            return;
        }

        List<Long> stale = clients.entrySet().stream()
                .filter(entry -> {
                    try {
                        entry.getValue().send(SseEmitter.event().name(eventName).data(json));
                        return false;
                    } catch (IOException ex) {
                        return true; // client déconnecté
                    }
                })
                .map(Map.Entry::getKey)
                .toList();

        stale.forEach(clients::remove);
        if (!stale.isEmpty()) {
            log.debug("[SSE] Nettoyage de {} client(s) inactif(s)", stale.size());
        }
    }
}

