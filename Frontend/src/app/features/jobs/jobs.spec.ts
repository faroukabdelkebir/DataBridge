import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';

import { Jobs } from './jobs';
import { MonitoringService } from '../../core/services/monitoring.service';
import { JobExecution, StepExecution } from '../../core/models/monitoring.model';

/** Fabrique un JobExecution minimal, surchargeable. */
function job(overrides: Partial<JobExecution> = {}): JobExecution {
  return {
    jobExecutionId: 1,
    jobInstanceId: 1,
    jobName: 'EXTRACT_CUSTOMERS',
    status: 'COMPLETED',
    exitCode: 'COMPLETED',
    exitMessage: null,
    startTime: '2026-07-28T10:00:00',
    endTime: '2026-07-28T10:00:05',
    createTime: '2026-07-28T10:00:00',
    durationSeconds: 5,
    running: false,
    failed: false,
    totalReadCount: 100,
    totalWriteCount: 100,
    totalSkipCount: 0,
    throughputPerSecond: 20,
    reconciliation: null,
    ...overrides,
  };
}

describe('Jobs (composant)', () => {
  let monitoring: {
    getRecentJobs: ReturnType<typeof vi.fn>;
    getJobSteps: ReturnType<typeof vi.fn>;
  };
  let translate: Partial<TranslateService>;

  function createComponent(jobs: JobExecution[] = []) {
    monitoring.getRecentJobs.mockReturnValue(of(jobs));
    TestBed.configureTestingModule({
      providers: [
        Jobs,
        { provide: MonitoringService, useValue: monitoring },
        { provide: TranslateService, useValue: translate },
      ],
    });
    return TestBed.inject(Jobs);
  }

  beforeEach(() => {
    monitoring = {
      getRecentJobs: vi.fn().mockReturnValue(of([])),
      getJobSteps: vi.fn().mockReturnValue(of([])),
    };
    translate = {
      // Signal-like accessor utilisé dans le composant (currentLang()).
      currentLang: (() => 'fr') as unknown as TranslateService['currentLang'],
      // Renvoie la clé telle quelle → statusLabel retombe sur la valeur brute.
      instant: ((key: string) => key) as TranslateService['instant'],
    };
  });

  describe('chargement et fenêtre de jours', () => {
    it('ne conserve que les jobs dans la fenêtre choisie', () => {
      vi.useFakeTimers();
      vi.setSystemTime(new Date('2026-07-28T12:00:00Z'));

      const recent = job({ jobExecutionId: 1, startTime: '2026-07-27T12:00:00Z' }); // dans 7 j
      const old = job({ jobExecutionId: 2, startTime: '2026-07-01T12:00:00Z' });     // hors 7 j
      const comp = createComponent([recent, old]);

      expect(comp.loading()).toBe(false);
      expect(comp.jobs().map((j) => j.jobExecutionId)).toEqual([1]);

      vi.useRealTimers();
    });

    it('ignore les jobs sans startTime', () => {
      const comp = createComponent([job({ startTime: null })]);
      expect(comp.jobs()).toHaveLength(0);
    });
  });

  describe('filtrage réactif', () => {
    it('filtre par terme de recherche (insensible à la casse)', () => {
      const comp = createComponent([
        job({ jobExecutionId: 1, jobName: 'EXTRACT_CUSTOMERS' }),
        job({ jobExecutionId: 2, jobName: 'LOAD_BILLING' }),
      ]);

      comp.search.set('custom');
      expect(comp.jobs().map((j) => j.jobExecutionId)).toEqual([1]);
    });

    it('filtre par statut', () => {
      const comp = createComponent([
        job({ jobExecutionId: 1, status: 'COMPLETED' }),
        job({ jobExecutionId: 2, status: 'FAILED' }),
      ]);

      comp.statusFilter.set('FAILED');
      expect(comp.jobs().map((j) => j.jobExecutionId)).toEqual([2]);
    });

    it('combine recherche et statut', () => {
      const comp = createComponent([
        job({ jobExecutionId: 1, jobName: 'EXTRACT_A', status: 'FAILED' }),
        job({ jobExecutionId: 2, jobName: 'EXTRACT_B', status: 'FAILED' }),
        job({ jobExecutionId: 3, jobName: 'EXTRACT_A', status: 'COMPLETED' }),
      ]);

      comp.search.set('extract_a');
      comp.statusFilter.set('FAILED');
      expect(comp.jobs().map((j) => j.jobExecutionId)).toEqual([1]);
    });
  });

  describe('statusClass', () => {
    it('associe chaque statut à sa classe de badge', () => {
      const comp = createComponent();
      expect(comp.statusClass('COMPLETED')).toBe('is-done');
      expect(comp.statusClass('FAILED')).toBe('is-failed');
      expect(comp.statusClass('ABANDONED')).toBe('is-failed');
      expect(comp.statusClass('STARTED')).toBe('is-running');
      expect(comp.statusClass('STARTING')).toBe('is-running');
      expect(comp.statusClass('UNKNOWN')).toBe('is-neutral');
      expect(comp.statusClass('STOPPED')).toBe('is-neutral');
    });
  });

  describe('statusLabel', () => {
    it('retombe sur la valeur brute quand la clé i18n est absente', () => {
      const comp = createComponent();
      // translate.instant renvoie la clé → statusLabel doit renvoyer le statut brut.
      expect(comp.statusLabel('COMPLETED')).toBe('COMPLETED');
    });

    it('renvoie le libellé traduit quand il existe', () => {
      translate.instant = ((key: string) =>
        key === 'jobs.state.FAILED' ? 'Échoué' : key) as TranslateService['instant'];
      const comp = createComponent();
      expect(comp.statusLabel('FAILED')).toBe('Échoué');
    });
  });

  describe('durationOf', () => {
    it('calcule la durée depuis les horodatages (précision ms)', () => {
      const comp = createComponent();
      const d = comp.durationOf({
        startTime: '2026-07-28T10:00:00',
        endTime: '2026-07-28T10:00:02.500',
        durationSeconds: 2,
      });
      expect(d).toBe(2.5);
    });

    it('retombe sur durationSeconds si un horodatage manque', () => {
      const comp = createComponent();
      expect(
        comp.durationOf({ startTime: '2026-07-28T10:00:00', endTime: null, durationSeconds: 7 }),
      ).toBe(7);
    });

    it('ignore une durée négative (fin avant début) et retombe sur durationSeconds', () => {
      const comp = createComponent();
      expect(
        comp.durationOf({
          startTime: '2026-07-28T10:00:05',
          endTime: '2026-07-28T10:00:00',
          durationSeconds: 3,
        }),
      ).toBe(3);
    });
  });

  describe('openSteps', () => {
    it('charge les steps du job sélectionné et ouvre le dialog', () => {
      const steps: StepExecution[] = [
        {
          stepExecutionId: 10,
          jobExecutionId: 1,
          stepName: 'read',
          status: 'COMPLETED',
          exitCode: 'COMPLETED',
          startTime: '2026-07-28T10:00:00',
          endTime: '2026-07-28T10:00:05',
          durationSeconds: 5,
          readCount: 100,
          writeCount: 100,
          filterCount: 0,
          skipCount: 0,
          rollbackCount: 0,
          commitCount: 1,
          throughputPerSecond: 20,
        },
      ];
      monitoring.getJobSteps.mockReturnValue(of(steps));
      const comp = createComponent([job()]);

      comp.openSteps(job({ jobExecutionId: 42 }));

      expect(monitoring.getJobSteps).toHaveBeenCalledWith(42);
      expect(comp.dialogVisible()).toBe(true);
      expect(comp.selectedJob()?.jobExecutionId).toBe(42);
      expect(comp.steps()).toEqual(steps);
      expect(comp.stepsLoading()).toBe(false);
    });
  });

  describe('exportCsv', () => {
    it('génère un CSV échappé et déclenche le téléchargement', () => {
      const comp = createComponent([
        job({
          jobName: 'JOB, WITH "COMMA"',
          status: 'COMPLETED',
          startTime: '2026-07-28T10:00:00',
          durationSeconds: 5,
          totalReadCount: 100,
          totalWriteCount: 90,
          totalSkipCount: 2,
        }),
      ]);

      // Capture le contenu texte passé au constructeur Blob.
      let csvContent = '';
      const RealBlob = globalThis.Blob;
      const blobSpy = vi
        .spyOn(globalThis, 'Blob')
        .mockImplementation(function (parts: BlobPart[] = [], opts?: BlobPropertyBag) {
          csvContent = (parts as string[]).join('');
          return new RealBlob(parts, opts);
        } as unknown as typeof Blob);

      const clickSpy = vi.fn();
      const anchor = document.createElement('a');
      anchor.click = clickSpy;
      vi.spyOn(document, 'createElement').mockReturnValue(anchor as HTMLAnchorElement);

      comp.exportCsv();

      expect(clickSpy).toHaveBeenCalledTimes(1);
      expect(anchor.download).toMatch(/^historique-jobs-\d{4}-\d{2}-\d{2}\.csv$/);

      const lines = csvContent.replace(/^﻿/, '').split('\r\n');
      expect(lines[0]).toBe('Job,Statut,Début,Durée (s),Lues,Écrites,Ignorées');
      // Le nom contient une virgule et des guillemets → doit être entouré de "" et échappé.
      expect(lines[1]).toBe(
        '"JOB, WITH ""COMMA""",COMPLETED,2026-07-28T10:00:00,5,100,90,2',
      );

      blobSpy.mockRestore();
    });
  });
});
