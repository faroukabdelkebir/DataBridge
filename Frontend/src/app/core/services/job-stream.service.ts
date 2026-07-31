import { Injectable, signal } from '@angular/core';
import { API } from '../config/api.config';
import { Alert, JobExecution } from '../models/monitoring.model';

/**
 * Flux SSE MonitoringMS — GET /stream/jobs.
 * Événements serveur : 'connected', 'jobs' (10 s), 'alerts' (30 s), 'heartbeat' (25 s).
 */
@Injectable({ providedIn: 'root' })
export class JobStreamService {
  private es: EventSource | null = null;
  private retryCount = 0;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;

  readonly liveJobs = signal<JobExecution[]>([]);
  readonly alerts = signal<Alert[]>([]);
  readonly connected = signal(false);

  connect(): void {
    if (this.es) return;                 // déjà connecté
    this.open();
  }

  disconnect(): void {
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
    this.es?.close();
    this.es = null;
    this.connected.set(false);
  }

  private open(): void {
    const es = new EventSource(API.monitoring.streamJobs);
    this.es = es;

    es.addEventListener('connected', () => {
      this.connected.set(true);
      this.retryCount = 0;
    });

    es.addEventListener('jobs', (e) => {
      const jobs = JSON.parse((e as MessageEvent).data) as JobExecution[];
      this.liveJobs.set(jobs.map((j) => ({
        ...j,
        startTime: toIsoDate(j.startTime),
        endTime: toIsoDate(j.endTime),
        createTime: toIsoDate(j.createTime),
      })));
    });

    es.addEventListener('alerts', (e) => {
      const alerts = JSON.parse((e as MessageEvent).data) as Alert[];
      this.alerts.set(alerts.map((a) => ({ ...a, triggeredAt: toIsoDate(a.triggeredAt)! })));
    });

    // 'heartbeat' : rien à faire, il maintient juste la connexion

    es.onerror = () => {
      this.connected.set(false);
      es.close();
      this.es = null;
      // Backoff exponentiel plafonné à 30 s
      const delay = Math.min(30_000, 1_000 * 2 ** this.retryCount++);
      this.retryTimer = setTimeout(() => this.open(), delay);
    };
  }
}

/** Jackson peut sérialiser LocalDateTime en [y,M,d,h,m,s,nano] : normalise en ISO. */
function toIsoDate(v: unknown): string | null {
  if (v == null) return null;
  if (Array.isArray(v)) {
    const [y, mo, d, h = 0, mi = 0, s = 0, nano = 0] = v as number[];
    const p = (n: number) => String(n).padStart(2, '0');
    const ms = String(Math.floor(nano / 1_000_000)).padStart(3, '0');
    return `${y}-${p(mo)}-${p(d)}T${p(h)}:${p(mi)}:${p(s)}.${ms}`;
  }
  return v as string;
}