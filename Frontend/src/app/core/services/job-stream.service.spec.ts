import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import { JobStreamService } from './job-stream.service';

/** Faux EventSource : capture les listeners et permet de simuler des événements. */
class FakeEventSource {
  static instances: FakeEventSource[] = [];
  onerror: (() => void) | null = null;
  closed = false;
  private listeners = new Map<string, ((e: unknown) => void)[]>();

  constructor(public url: string) {
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, cb: (e: unknown) => void): void {
    const arr = this.listeners.get(type) ?? [];
    arr.push(cb);
    this.listeners.set(type, arr);
  }

  emit(type: string, data?: string): void {
    for (const cb of this.listeners.get(type) ?? []) cb({ data } as MessageEvent);
  }

  close(): void {
    this.closed = true;
  }
}

describe('JobStreamService', () => {
  let service: JobStreamService;
  const originalES = globalThis.EventSource;

  beforeEach(() => {
    FakeEventSource.instances = [];
    (globalThis as unknown as { EventSource: unknown }).EventSource =
      FakeEventSource as unknown as typeof EventSource;
    vi.useFakeTimers();
    service = new JobStreamService();
  });

  afterEach(() => {
    service.disconnect();
    vi.useRealTimers();
    (globalThis as unknown as { EventSource: unknown }).EventSource = originalES;
  });

  it('connect ouvre une seule connexion SSE', () => {
    service.connect();
    service.connect(); // idempotent
    expect(FakeEventSource.instances).toHaveLength(1);
    expect(FakeEventSource.instances[0].url).toContain('/stream/jobs');
  });

  it('l’événement "connected" passe l’état à connecté', () => {
    service.connect();
    expect(service.connected()).toBe(false);
    FakeEventSource.instances[0].emit('connected');
    expect(service.connected()).toBe(true);
  });

  it('l’événement "jobs" met à jour le signal liveJobs', () => {
    service.connect();
    FakeEventSource.instances[0].emit(
      'jobs',
      JSON.stringify([{ jobExecutionId: 1, jobName: 'X', startTime: '2026-07-28T10:00:00' }]),
    );
    expect(service.liveJobs()).toHaveLength(1);
    expect(service.liveJobs()[0].jobName).toBe('X');
  });

  it('normalise les dates tableau Jackson en ISO', () => {
    service.connect();
    // LocalDateTime sérialisé [y,M,d,h,m,s,nano]
    FakeEventSource.instances[0].emit(
      'jobs',
      JSON.stringify([{ jobExecutionId: 1, jobName: 'X', startTime: [2026, 7, 28, 14, 5, 9, 123000000] }]),
    );
    expect(service.liveJobs()[0].startTime).toBe('2026-07-28T14:05:09.123');
  });

  it('l’événement "alerts" met à jour le signal alerts', () => {
    service.connect();
    FakeEventSource.instances[0].emit(
      'alerts',
      JSON.stringify([{ type: 'SLA', severity: 'WARNING', triggeredAt: '2026-07-28T10:00:00' }]),
    );
    expect(service.alerts()).toHaveLength(1);
    expect(service.alerts()[0].severity).toBe('WARNING');
  });

  it('reconnecte avec backoff exponentiel après une erreur', () => {
    service.connect();
    const first = FakeEventSource.instances[0];

    first.onerror!(); // déclenche la fermeture + retry planifié
    expect(service.connected()).toBe(false);
    expect(first.closed).toBe(true);

    // Premier backoff = 1000 ms (2^0)
    vi.advanceTimersByTime(1000);
    expect(FakeEventSource.instances).toHaveLength(2);
  });

  it('disconnect ferme la connexion et annule le retry', () => {
    service.connect();
    service.disconnect();
    expect(FakeEventSource.instances[0].closed).toBe(true);
    expect(service.connected()).toBe(false);
  });
});
