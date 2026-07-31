import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { Reconciliation } from './reconciliation';
import { MonitoringService } from '../../core/services/monitoring.service';
import { Reconciliation as ReconciliationModel } from '../../core/models/monitoring.model';

describe('Reconciliation (composant)', () => {
  let monitoring: { getReconciliation: ReturnType<typeof vi.fn> };

  function create(ret = of<ReconciliationModel[]>([])) {
    monitoring.getReconciliation.mockReturnValue(ret);
    TestBed.configureTestingModule({
      providers: [Reconciliation, { provide: MonitoringService, useValue: monitoring }],
    });
    return TestBed.inject(Reconciliation);
  }

  beforeEach(() => {
    monitoring = { getReconciliation: vi.fn().mockReturnValue(of([])) };
  });

  it('charge les lignes de réconciliation', () => {
    const rows: ReconciliationModel[] = [
      { tableName: 'T', businessDate: 20260728, oracleCount: 10, sqlServerCount: 10, delta: 0, reconciliationRate: 100, reconciled: true, status: 'OK' },
    ];
    const comp = create(of(rows));
    expect(comp.rows()).toEqual(rows);
    expect(comp.loading()).toBe(false);
  });

  it('coupe le loading en cas d’erreur', () => {
    const comp = create(throwError(() => new Error('x')));
    expect(comp.loading()).toBe(false);
  });
});
