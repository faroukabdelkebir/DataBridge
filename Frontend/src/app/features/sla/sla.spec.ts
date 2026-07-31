import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { Sla } from './sla';
import { MonitoringService } from '../../core/services/monitoring.service';
import { SlaStatus } from '../../core/models/monitoring.model';

describe('Sla (composant)', () => {
  let monitoring: { getSla: ReturnType<typeof vi.fn> };

  function create(ret = of<SlaStatus[]>([])) {
    monitoring.getSla.mockReturnValue(ret);
    TestBed.configureTestingModule({
      providers: [Sla, { provide: MonitoringService, useValue: monitoring }],
    });
    return TestBed.inject(Sla);
  }

  beforeEach(() => {
    monitoring = { getSla: vi.fn().mockReturnValue(of([])) };
  });

  it('charge les SLA à la construction', () => {
    const rows: SlaStatus[] = [
      { jobName: 'J', slaDeadline: '06:00:00', lastCompletionTime: null, slaBreached: true, lateBySeconds: 120, status: 'LATE' },
    ];
    const comp = create(of(rows));
    expect(comp.slas()).toEqual(rows);
    expect(comp.loading()).toBe(false);
  });

  it('coupe le loading en cas d’erreur', () => {
    const comp = create(throwError(() => new Error('x')));
    expect(comp.loading()).toBe(false);
  });
});
