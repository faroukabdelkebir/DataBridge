import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ConfirmationService, MessageService } from 'primeng/api';

import { BatchControl } from './batch-control';
import { ExtractorService } from '../../core/services/extractor.service';
import { MonitoringService } from '../../core/services/monitoring.service';
import { JobStreamService } from '../../core/services/job-stream.service';
import { JobExecution } from '../../core/models/monitoring.model';
import { BatchConfigRecord } from '../../core/models/batch-config.model';

function runningJob(name: string): JobExecution {
  return {
    jobExecutionId: 1, jobInstanceId: 1, jobName: name, status: 'STARTED',
    exitCode: null, exitMessage: null, startTime: null, endTime: null, createTime: null,
    durationSeconds: null, running: true, failed: false,
    totalReadCount: 0, totalWriteCount: 0, totalSkipCount: 0,
    throughputPerSecond: null, reconciliation: null,
  };
}

function config(o: Partial<BatchConfigRecord> = {}): BatchConfigRecord {
  return { id: 'B1', enabled: true, scheduler: false, schedulerTime: null, ...(o as object) } as BatchConfigRecord;
}

type Fn = ReturnType<typeof vi.fn>;

describe('BatchControl (composant)', () => {
  let extractor: {
    startMain: Fn; startCustomer: Fn; startFtHis: Fn; startBillDetails: Fn; stopMain: Fn;
    getColumns: Fn; getBatchConfigs: Fn; toggleBatchEnabled: Fn; updateBatchScheduler: Fn; setColumnEnabled: Fn;
  };
  let monitoring: { getSchemaSummary: ReturnType<typeof vi.fn> };
  let stream: { connect: ReturnType<typeof vi.fn>; disconnect: ReturnType<typeof vi.fn>; connected: unknown; liveJobs: ReturnType<typeof signal<JobExecution[]>> };
  let toast: { add: ReturnType<typeof vi.fn> };
  let confirm: { confirm: ReturnType<typeof vi.fn> };
  let liveJobs: ReturnType<typeof signal<JobExecution[]>>;

  function create() {
    TestBed.configureTestingModule({
      providers: [
        BatchControl,
        { provide: ExtractorService, useValue: extractor },
        { provide: MonitoringService, useValue: monitoring },
        { provide: JobStreamService, useValue: stream },
        { provide: MessageService, useValue: toast },
        { provide: ConfirmationService, useValue: confirm },
        { provide: TranslateService, useValue: { instant: (k: string) => k } },
      ],
    });
    return TestBed.inject(BatchControl);
  }

  beforeEach(() => {
    liveJobs = signal<JobExecution[]>([]);
    extractor = {
      startMain: vi.fn().mockReturnValue(of(undefined)),
      startCustomer: vi.fn().mockReturnValue(of(undefined)),
      startFtHis: vi.fn().mockReturnValue(of(undefined)),
      startBillDetails: vi.fn().mockReturnValue(of(undefined)),
      stopMain: vi.fn().mockReturnValue(of(undefined)),
      getColumns: vi.fn().mockReturnValue(of([])),
      getBatchConfigs: vi.fn().mockReturnValue(of([])),
      toggleBatchEnabled: vi.fn().mockReturnValue(of({})),
      updateBatchScheduler: vi.fn().mockReturnValue(of({})),
      setColumnEnabled: vi.fn().mockReturnValue(of({})),
    };
    monitoring = { getSchemaSummary: vi.fn().mockReturnValue(of([])) };
    stream = { connect: vi.fn(), disconnect: vi.fn(), connected: signal(true), liveJobs };
    toast = { add: vi.fn() };
    confirm = { confirm: vi.fn() };
  });

  it('ouvre le flux SSE à la construction', () => {
    create();
    expect(stream.connect).toHaveBeenCalled();
  });

  describe('jobs en cours (via SSE)', () => {
    it('runningNames reflète les jobs en exécution', () => {
      const comp = create();
      liveJobs.set([runningJob('T24'), runningJob('CUSTOMER.JOB')]);
      expect(comp.runningNames().has('T24')).toBe(true);
      expect(comp.mainRunning()).toBe(true);
    });

    it('isRunning teste le nom du job de la carte', () => {
      const comp = create();
      liveJobs.set([runningJob('CUSTOMER.JOB')]);
      const customerCard = comp.jobs.find((j) => j.key === 'customer')!;
      const mainCard = comp.jobs.find((j) => j.key === 'main')!;
      expect(comp.isRunning(customerCard)).toBe(true);
      expect(comp.isRunning(mainCard)).toBe(false);
    });
  });

  describe('askStart', () => {
    it('demande confirmation puis démarre le job à l’acceptation', () => {
      const comp = create();
      const mainCard = comp.jobs.find((j) => j.key === 'main')!;
      comp.askStart(mainCard);

      expect(confirm.confirm).toHaveBeenCalled();
      confirm.confirm.mock.calls[0][0].accept();

      expect(extractor.startMain).toHaveBeenCalled();
      expect(comp.launching()).toBeNull();
      expect(toast.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    });

    it('affiche une erreur si le démarrage échoue', () => {
      extractor.startMain.mockReturnValue(throwError(() => ({ error: { message: 'already running' } })));
      const comp = create();
      const mainCard = comp.jobs.find((j) => j.key === 'main')!;
      comp.askStart(mainCard);
      confirm.confirm.mock.calls[0][0].accept();

      expect(toast.add).toHaveBeenCalledWith(
        expect.objectContaining({ severity: 'error', detail: 'already running' }),
      );
    });
  });

  describe('askStop', () => {
    it('arrête l’extraction T24 à l’acceptation', () => {
      const comp = create();
      comp.askStop();
      confirm.confirm.mock.calls[0][0].accept();
      expect(extractor.stopMain).toHaveBeenCalled();
      expect(comp.stopping()).toBe(false);
    });
  });

  describe('toggleConfigScheduler', () => {
    it('propose 06:00 par défaut si aucune heure définie', () => {
      const comp = create();
      comp.configs.set([config({ id: 'B1', scheduler: false, schedulerTime: null })]);

      comp.toggleConfigScheduler(config({ id: 'B1', scheduler: false, schedulerTime: null }), true);

      expect(extractor.updateBatchScheduler).toHaveBeenCalledWith('B1', true, '06:00');
      // MAJ optimiste appliquée à la liste
      expect(comp.configs()[0].schedulerTime).toBe('06:00');
    });

    it('conserve l’heure existante quand elle est définie', () => {
      const comp = create();
      comp.configs.set([config({ id: 'B1', scheduler: true, schedulerTime: '08:00' })]);

      comp.toggleConfigScheduler(config({ id: 'B1', scheduler: true, schedulerTime: '08:00' }), false);

      expect(extractor.updateBatchScheduler).toHaveBeenCalledWith('B1', false, null);
    });
  });

  describe('patchConfig (via toggleConfigEnabled)', () => {
    it('applique une MAJ optimiste puis confirme sur succès', () => {
      const comp = create();
      comp.configs.set([config({ id: 'B1', enabled: true })]);

      comp.toggleConfigEnabled(config({ id: 'B1', enabled: true }), false);

      expect(comp.configs()[0].enabled).toBe(false);
      expect(comp.pendingConfig()).toBeNull();
      expect(toast.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    });

    it('revient en arrière sur erreur', () => {
      extractor.toggleBatchEnabled.mockReturnValue(throwError(() => ({ error: {} })));
      const comp = create();
      comp.configs.set([config({ id: 'B1', enabled: true })]);

      comp.toggleConfigEnabled(config({ id: 'B1', enabled: true }), false);

      // rollback : la valeur d'origine est restaurée
      expect(comp.configs()[0].enabled).toBe(true);
      expect(toast.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    });
  });

  describe('toggleColumn', () => {
    it('bascule de façon optimiste puis remplace par la réponse serveur', () => {
      extractor.setColumnEnabled.mockReturnValue(of({ id: 'c1', tableName: 'T', columnName: 'C', enabled: true }));
      const comp = create();
      comp.columns.set([{ id: 'c1', tableName: 'T', columnName: 'C', enabled: false } as never]);

      comp.toggleColumn({ id: 'c1', tableName: 'T', columnName: 'C', enabled: false } as never, true);

      expect(extractor.setColumnEnabled).toHaveBeenCalledWith('T', 'C', true);
      expect(comp.columns()[0].enabled).toBe(true);
      expect(comp.pendingColumn()).toBeNull();
    });

    it('revient en arrière sur erreur', () => {
      extractor.setColumnEnabled.mockReturnValue(throwError(() => new Error('x')));
      const comp = create();
      comp.columns.set([{ id: 'c1', tableName: 'T', columnName: 'C', enabled: false } as never]);

      comp.toggleColumn({ id: 'c1', tableName: 'T', columnName: 'C', enabled: false } as never, true);

      expect(comp.columns()[0].enabled).toBe(false);
      expect(toast.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    });
  });
});
