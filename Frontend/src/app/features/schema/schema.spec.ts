import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { Schema } from './schema';
import { MonitoringService } from '../../core/services/monitoring.service';

describe('Schema (composant)', () => {
  let monitoring: {
    getSchemaSummary: ReturnType<typeof vi.fn>;
    getSchemaAudit: ReturnType<typeof vi.fn>;
  };

  function create() {
    TestBed.configureTestingModule({
      providers: [Schema, { provide: MonitoringService, useValue: monitoring }],
    });
    return TestBed.inject(Schema);
  }

  beforeEach(() => {
    monitoring = {
      getSchemaSummary: vi.fn().mockReturnValue(of([])),
      getSchemaAudit: vi.fn().mockReturnValue(of([])),
    };
  });

  it('charge le résumé et l’audit (100 dernières entrées)', () => {
    monitoring.getSchemaSummary.mockReturnValue(of([{ tableName: 'T' }]));
    monitoring.getSchemaAudit.mockReturnValue(of([{ id: 1 }]));

    const comp = create();

    expect(comp.summary()).toHaveLength(1);
    expect(comp.audit()).toHaveLength(1);
    expect(monitoring.getSchemaAudit).toHaveBeenCalledWith(100);
    expect(comp.loading()).toBe(false);
  });

  it('coupe le loading si le résumé échoue', () => {
    monitoring.getSchemaSummary.mockReturnValue(throwError(() => new Error('x')));
    const comp = create();
    expect(comp.loading()).toBe(false);
  });
});
