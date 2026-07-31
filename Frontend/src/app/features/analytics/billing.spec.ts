import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

import { BillingAnalytics } from './billing';
import { AnalyticsService } from '../../core/services/analytics.service';
import { ThemeService } from '../../core/services/theme.service';
import { BillingSummary } from '../../core/models/analytics.model';

function summary(o: Partial<BillingSummary> = {}): BillingSummary {
  return {
    nbFactures: 200, deltaFacturesPct: 4, montantFacture: 500000,
    encoursTotal: 120000, pctPayees: 76, delaiMoyenReglement: 12,
    ...(o as object),
  } as BillingSummary;
}

describe('BillingAnalytics (composant)', () => {
  let analytics: Record<
    'getBillYears' | 'getBillingSummary' | 'getBillingMonthly' | 'getBillingInsights' |
    'getBillingColumnsStatus' | 'getBillingDimensions' | 'getBillingBreakdown',
    ReturnType<typeof vi.fn>
  >;

  function create() {
    TestBed.configureTestingModule({
      providers: [
        BillingAnalytics,
        { provide: AnalyticsService, useValue: analytics },
        { provide: ThemeService, useValue: { darkMode: signal(false) } },
        { provide: TranslateService, useValue: { instant: (k: string) => k, currentLang: () => 'fr' } },
      ],
    });
    return TestBed.inject(BillingAnalytics);
  }

  beforeEach(() => {
    analytics = {
      getBillYears: vi.fn().mockReturnValue(of([2025])),
      getBillingSummary: vi.fn().mockReturnValue(of(summary())),
      getBillingMonthly: vi.fn().mockReturnValue(of([])),
      getBillingInsights: vi.fn().mockReturnValue(of([])),
      getBillingColumnsStatus: vi.fn().mockReturnValue(of({ OS_TOTAL_AMOUNT: true })),
      getBillingDimensions: vi.fn().mockReturnValue(of(['aging', 'currency'])),
      getBillingBreakdown: vi.fn().mockReturnValue(of([])),
    };
  });

  it('charge et déclenche le breakdown sur la dimension par défaut valide', () => {
    const comp = create();
    expect(comp.selectedYear()).toBe(2025);
    // 'aging' est la dimension par défaut et est présente → breakdown appelé
    expect(analytics.getBillingBreakdown).toHaveBeenCalledWith('aging', 2025);
    expect(comp.loading()).toBe(false);
  });

  it('bascule sur la première dimension si la sélection n’est pas disponible', () => {
    analytics.getBillingDimensions.mockReturnValue(of(['currency', 'sector']));
    const comp = create();
    expect(comp.selectedDimension()).toBe('currency');
  });

  it('positionne error si le forkJoin échoue', () => {
    analytics.getBillingSummary.mockReturnValue(throwError(() => new Error('x')));
    const comp = create();
    expect(comp.error()).toBe(true);
  });

  describe('cards conditionnelles', () => {
    it('inclut toutes les cartes quand les champs sont présents', () => {
      const comp = create();
      expect(comp.cards().map((c) => c.key)).toEqual(['bills', 'billed', 'outstanding', 'paid', 'delay']);
    });

    it('réduit aux cartes non nulles', () => {
      analytics.getBillingSummary.mockReturnValue(of(summary({
        montantFacture: null, encoursTotal: null, pctPayees: null, delaiMoyenReglement: null,
      })));
      const comp = create();
      expect(comp.cards().map((c) => c.key)).toEqual(['bills']);
    });
  });

  it('onDimensionChange recharge le breakdown', () => {
    const comp = create();
    analytics.getBillingBreakdown.mockClear();
    comp.onDimensionChange('currency');
    expect(comp.selectedDimension()).toBe('currency');
    expect(analytics.getBillingBreakdown).toHaveBeenCalledWith('currency', 2025);
  });

  it('hasAmounts reflète la colonne OS_TOTAL_AMOUNT', () => {
    const comp = create();
    expect(comp.hasAmounts()).toBe(true);
  });
});
