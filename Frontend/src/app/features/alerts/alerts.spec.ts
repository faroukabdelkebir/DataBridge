import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MessageService } from 'primeng/api';

import { Alerts } from './alerts';
import { MonitoringService } from '../../core/services/monitoring.service';
import { Alert } from '../../core/models/monitoring.model';

function alert(overrides: Partial<Alert> = {}): Alert {
  return {
    alertKey: 'k1', type: 'SLA', severity: 'WARNING', jobName: 'J',
    message: 'msg', triggeredAt: '2026-07-28T10:00:00', resolved: false,
    ...overrides,
  };
}

describe('Alerts (composant)', () => {
  let monitoring: { getAlerts: ReturnType<typeof vi.fn>; resolveAlert: ReturnType<typeof vi.fn> };
  let toast: { add: ReturnType<typeof vi.fn> };

  function create(list: Alert[] = []) {
    monitoring.getAlerts.mockReturnValue(of(list));
    TestBed.configureTestingModule({
      providers: [
        Alerts,
        { provide: MonitoringService, useValue: monitoring },
        { provide: MessageService, useValue: toast },
        { provide: TranslateService, useValue: { instant: (k: string) => k } },
      ],
    });
    return TestBed.inject(Alerts);
  }

  beforeEach(() => {
    monitoring = {
      getAlerts: vi.fn().mockReturnValue(of([])),
      resolveAlert: vi.fn().mockReturnValue(of({})),
    };
    toast = { add: vi.fn() };
  });

  it('charge les alertes à la construction', () => {
    const comp = create([alert()]);
    expect(comp.alerts()).toHaveLength(1);
    expect(comp.loading()).toBe(false);
  });

  it('coupe le loading en cas d’erreur de chargement', () => {
    monitoring.getAlerts.mockReturnValue(throwError(() => new Error('x')));
    const comp = create();
    expect(comp.loading()).toBe(false);
  });

  describe('alertSeverity', () => {
    it('mappe les niveaux vers les sévérités PrimeNG', () => {
      const comp = create();
      expect(comp.alertSeverity('CRITICAL')).toBe('danger');
      expect(comp.alertSeverity('high')).toBe('danger');
      expect(comp.alertSeverity('WARNING')).toBe('warn');
      expect(comp.alertSeverity('MEDIUM')).toBe('warn');
      expect(comp.alertSeverity('INFO')).toBe('info');
      expect(comp.alertSeverity('')).toBe('info');
    });
  });

  describe('resolve', () => {
    it('ne fait rien sans alertKey', () => {
      const comp = create();
      comp.resolve(alert({ alertKey: undefined }));
      expect(monitoring.resolveAlert).not.toHaveBeenCalled();
    });

    it('retire l’alerte résolue de la liste et notifie', () => {
      const comp = create([alert({ alertKey: 'k1' }), alert({ alertKey: 'k2' })]);
      comp.resolve(alert({ alertKey: 'k1' }));

      expect(monitoring.resolveAlert).toHaveBeenCalledWith('k1');
      expect(comp.alerts().map((a) => a.alertKey)).toEqual(['k2']);
      expect(comp.resolving()).toBeNull();
      expect(toast.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    });

    it('sur erreur : conserve la liste et notifie une erreur', () => {
      monitoring.resolveAlert.mockReturnValue(throwError(() => new Error('x')));
      const comp = create([alert({ alertKey: 'k1' })]);
      comp.resolve(alert({ alertKey: 'k1' }));

      expect(comp.alerts()).toHaveLength(1);
      expect(comp.resolving()).toBeNull();
      expect(toast.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    });
  });
});
