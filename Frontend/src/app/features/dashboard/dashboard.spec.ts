import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

import { Dashboard } from './dashboard';
import { MonitoringService } from '../../core/services/monitoring.service';
import { JobStreamService } from '../../core/services/job-stream.service';
import { ThemeService } from '../../core/services/theme.service';
import { DashboardSummary, JobExecution, Alert } from '../../core/models/monitoring.model';

function summary(o: Partial<DashboardSummary> = {}): DashboardSummary {
  return {
    runningJobsCount: 2, completedTodayCount: 10, failedTodayCount: 1, successRate7d: 95,
    cobStatus: 'OPEN', lastBusinessDate: '2026-07-28',
    liveJobs: [], recentExecutions: [], activeAlerts: [], generatedAt: '2026-07-28T10:00:00',
    ...o,
  };
}

describe('Dashboard (composant)', () => {
  let monitoring: {
    getSummary: ReturnType<typeof vi.fn>;
    getKpis: ReturnType<typeof vi.fn>;
    getRecentJobs: ReturnType<typeof vi.fn>;
  };
  let streamLiveJobs: ReturnType<typeof signal<JobExecution[]>>;
  let streamAlerts: ReturnType<typeof signal<Alert[]>>;

  function create() {
    TestBed.configureTestingModule({
      providers: [
        Dashboard,
        { provide: MonitoringService, useValue: monitoring },
        {
          provide: JobStreamService,
          useValue: { connect: vi.fn(), disconnect: vi.fn(), connected: signal(true), liveJobs: streamLiveJobs, alerts: streamAlerts },
        },
        { provide: ThemeService, useValue: { darkMode: signal(false) } },
        { provide: TranslateService, useValue: { instant: (k: string) => k, currentLang: () => 'fr' } },
      ],
    });
    return TestBed.inject(Dashboard);
  }

  beforeEach(() => {
    streamLiveJobs = signal<JobExecution[]>([]);
    streamAlerts = signal<Alert[]>([]);
    monitoring = {
      getSummary: vi.fn().mockReturnValue(of(summary())),
      getKpis: vi.fn().mockReturnValue(of({ volumeTrend: [], rowsByTable: {} })),
      getRecentJobs: vi.fn().mockReturnValue(of([])),
    };
  });

  describe('load', () => {
    it('remplit summary/kpi et trie les jobs récents par début décroissant', () => {
      monitoring.getRecentJobs.mockReturnValue(of([
        { jobExecutionId: 1, jobName: 'A', startTime: '2026-07-01T10:00:00' },
        { jobExecutionId: 2, jobName: 'B', startTime: '2026-07-28T10:00:00' },
      ] as JobExecution[]));

      const comp = create();

      expect(comp.summary()).not.toBeNull();
      expect(comp.recentJobs().map((j) => j.jobExecutionId)).toEqual([2, 1]);
      expect(comp.loading()).toBe(false);
      expect(comp.error()).toBe(false);
    });

    it('positionne error sur échec', () => {
      monitoring.getSummary.mockReturnValue(throwError(() => new Error('x')));
      const comp = create();
      expect(comp.error()).toBe(true);
      expect(comp.loading()).toBe(false);
    });
  });

  describe('cards (KPI)', () => {
    it('est vide tant que le summary est absent', () => {
      monitoring.getSummary.mockReturnValue(throwError(() => new Error('x')));
      const comp = create();
      expect(comp.cards()).toEqual([]);
    });

    it('produit 4 cartes depuis le summary', () => {
      const comp = create();
      const cards = comp.cards();
      expect(cards.map((c) => c.key)).toEqual(['running', 'completed', 'failed', 'success7d']);
      expect(cards[0].value).toBe(2);
      expect(cards[3].format).toBe('percent');
    });
  });

  describe('liveJobs / activeAlerts (repli SSE → summary)', () => {
    it('utilise le flux SSE quand il a des données', () => {
      monitoring.getSummary.mockReturnValue(of(summary({ liveJobs: [{ jobExecutionId: 9 } as JobExecution] })));
      const comp = create();
      streamLiveJobs.set([{ jobExecutionId: 1 } as JobExecution]);
      expect(comp.liveJobs().map((j) => j.jobExecutionId)).toEqual([1]);
    });

    it('retombe sur le summary quand le flux est vide', () => {
      monitoring.getSummary.mockReturnValue(of(summary({ liveJobs: [{ jobExecutionId: 9 } as JobExecution] })));
      const comp = create();
      expect(comp.liveJobs().map((j) => j.jobExecutionId)).toEqual([9]);
    });
  });

  describe('helpers d’affichage', () => {
    it('statusSeverity mappe les statuts', () => {
      const comp = create();
      expect(comp.statusSeverity('COMPLETED')).toBe('success');
      expect(comp.statusSeverity('FAILED')).toBe('danger');
      expect(comp.statusSeverity('STARTING')).toBe('info');
      expect(comp.statusSeverity('ABANDONED')).toBe('warn');
      expect(comp.statusSeverity('UNKNOWN')).toBe('secondary');
    });

    it('statusClass mappe vers les classes de badge', () => {
      const comp = create();
      expect(comp.statusClass('COMPLETED')).toBe('is-done');
      expect(comp.statusClass('FAILED')).toBe('is-failed');
      expect(comp.statusClass('STARTED')).toBe('is-running');
      expect(comp.statusClass('STOPPED')).toBe('is-neutral');
    });

    it('durationOf calcule depuis les horodatages sinon retombe', () => {
      const comp = create();
      expect(comp.durationOf({ startTime: '2026-07-28T10:00:00', endTime: '2026-07-28T10:00:02', durationSeconds: 2 })).toBe(2);
      expect(comp.durationOf({ startTime: null, endTime: null, durationSeconds: 7 })).toBe(7);
    });

    it('alertSeverity mappe les niveaux', () => {
      const comp = create();
      expect(comp.alertSeverity('critical')).toBe('danger');
      expect(comp.alertSeverity('WARNING')).toBe('warn');
      expect(comp.alertSeverity('INFO')).toBe('info');
    });
  });
});
