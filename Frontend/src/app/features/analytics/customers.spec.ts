import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

import { CustomersAnalytics } from './customers';
import { AnalyticsService } from '../../core/services/analytics.service';
import { ThemeService } from '../../core/services/theme.service';
import { CustomersSummary } from '../../core/models/analytics.model';

function summary(o: Partial<CustomersSummary> = {}): CustomersSummary {
  return {
    totalClients: 1000, ageMoyen: 41, nouveauxClients: 120, deltaNouveauxPct: 6,
    pctFemmes: 48, nbNationalites: 12, nbGestionnaires: 5,
    ...(o as object),
  } as CustomersSummary;
}

describe('CustomersAnalytics (composant)', () => {
  let analytics: Record<
    'getAcquisitionYears' | 'getCustomersSummary' | 'getAgeHistogram' | 'getGenerations' |
    'getNewCustomersMonthly' | 'getCustomersInsights' | 'getCustomersColumnsStatus' |
    'getCustomerDimensions' | 'getCustomersBreakdown',
    ReturnType<typeof vi.fn>
  >;

  function create() {
    TestBed.configureTestingModule({
      providers: [
        CustomersAnalytics,
        { provide: AnalyticsService, useValue: analytics },
        { provide: ThemeService, useValue: { darkMode: signal(false) } },
        { provide: TranslateService, useValue: { instant: (k: string) => k, currentLang: () => 'fr' } },
      ],
    });
    return TestBed.inject(CustomersAnalytics);
  }

  beforeEach(() => {
    analytics = {
      getAcquisitionYears: vi.fn().mockReturnValue(of([2025])),
      getCustomersSummary: vi.fn().mockReturnValue(of(summary())),
      getAgeHistogram: vi.fn().mockReturnValue(of([])),
      getGenerations: vi.fn().mockReturnValue(of([])),
      getNewCustomersMonthly: vi.fn().mockReturnValue(of([])),
      getCustomersInsights: vi.fn().mockReturnValue(of([])),
      getCustomersColumnsStatus: vi.fn().mockReturnValue(of({ DATE_AC_OPND: true })),
      getCustomerDimensions: vi.fn().mockReturnValue(of(['sector', 'gender'])),
      getCustomersBreakdown: vi.fn().mockReturnValue(of([])),
    };
  });

  it('charge et déclenche le breakdown sur la dimension par défaut valide', () => {
    const comp = create();
    // 'sector' est la dimension par défaut et présente
    expect(analytics.getCustomersBreakdown).toHaveBeenCalledWith('sector');
    expect(comp.loading()).toBe(false);
  });

  it('bascule sur la première dimension si "sector" est absente', () => {
    analytics.getCustomerDimensions.mockReturnValue(of(['gender', 'nationality']));
    const comp = create();
    expect(comp.selectedDimension()).toBe('gender');
  });

  it('positionne error si le forkJoin échoue', () => {
    analytics.getCustomersSummary.mockReturnValue(throwError(() => new Error('x')));
    const comp = create();
    expect(comp.error()).toBe(true);
  });

  describe('cards', () => {
    it('carte nationalités quand nbNationalites est défini', () => {
      const comp = create();
      expect(comp.cards().map((c) => c.key)).toEqual(['clients', 'age', 'new', 'women', 'nat']);
    });

    it('carte gestionnaires en repli quand nbNationalites est null', () => {
      analytics.getCustomersSummary.mockReturnValue(of(summary({ nbNationalites: null })));
      const comp = create();
      const keys = comp.cards().map((c) => c.key);
      expect(keys).toContain('officers');
      expect(keys).not.toContain('nat');
    });
  });

  it('onDimensionChange recharge le breakdown', () => {
    const comp = create();
    analytics.getCustomersBreakdown.mockClear();
    comp.onDimensionChange('gender');
    expect(comp.selectedDimension()).toBe('gender');
    expect(analytics.getCustomersBreakdown).toHaveBeenCalledWith('gender');
  });

  it('hasAcquisition reflète la colonne DATE_AC_OPND', () => {
    const comp = create();
    expect(comp.hasAcquisition()).toBe(true);
  });
});
