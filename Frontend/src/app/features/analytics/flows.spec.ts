import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

import { Flows } from './flows';
import { AnalyticsService } from '../../core/services/analytics.service';
import { ThemeService } from '../../core/services/theme.service';
import { FlowsSummary } from '../../core/models/analytics.model';

function summary(o: Partial<FlowsSummary> = {}): FlowsSummary {
  return {
    nbOperations: 500, deltaOpsPct: 3, montantTotal: 1_000_000, deltaMontantPct: 8,
    ticketMoyen: 2000, nbDevises: 4, nbTypes: 6,
    ...(o as object),
  } as FlowsSummary;
}

describe('Flows (composant)', () => {
  let analytics: Record<
    'getYears' | 'getFlowsSummary' | 'getMonthlyTransfers' | 'getFlowsByCurrency' |
    'getFlowsByType' | 'getActivityHeatmap' | 'getFlowsInsights' | 'getFlowsColumnsStatus',
    ReturnType<typeof vi.fn>
  >;

  function create() {
    TestBed.configureTestingModule({
      providers: [
        Flows,
        { provide: AnalyticsService, useValue: analytics },
        { provide: ThemeService, useValue: { darkMode: signal(false) } },
        { provide: TranslateService, useValue: { instant: (k: string) => k, currentLang: () => 'fr' } },
      ],
    });
    return TestBed.inject(Flows);
  }

  beforeEach(() => {
    analytics = {
      getYears: vi.fn().mockReturnValue(of([2025])),
      getFlowsSummary: vi.fn().mockReturnValue(of(summary())),
      getMonthlyTransfers: vi.fn().mockReturnValue(of([])),
      getFlowsByCurrency: vi.fn().mockReturnValue(of([])),
      getFlowsByType: vi.fn().mockReturnValue(of([])),
      getActivityHeatmap: vi.fn().mockReturnValue(of([])),
      getFlowsInsights: vi.fn().mockReturnValue(of([])),
      getFlowsColumnsStatus: vi.fn().mockReturnValue(of({})),
    };
  });

  it('charge et pré-sélectionne l’année la plus récente', () => {
    const comp = create();
    expect(comp.selectedYear()).toBe(2025);
    expect(comp.loading()).toBe(false);
  });

  it('positionne error si le forkJoin échoue', () => {
    analytics.getFlowsSummary.mockReturnValue(throwError(() => new Error('x')));
    const comp = create();
    expect(comp.error()).toBe(true);
  });

  describe('cards conditionnelles', () => {
    it('inclut toutes les cartes quand les champs sont présents', () => {
      const comp = create();
      expect(comp.cards().map((c) => c.key)).toEqual(['ops', 'amount', 'ticket', 'currencies', 'types']);
    });

    it('omet montant/ticket/devises/types quand ils sont null', () => {
      analytics.getFlowsSummary.mockReturnValue(of(summary({
        montantTotal: null, ticketMoyen: null, nbDevises: null, nbTypes: null,
      })));
      const comp = create();
      expect(comp.cards().map((c) => c.key)).toEqual(['ops']);
    });
  });

  describe('statut des colonnes enrichies', () => {
    it('sépare colonnes présentes et manquantes', () => {
      analytics.getFlowsColumnsStatus.mockReturnValue(of({ AMOUNT_DEBITED: true, CURRENCY: false }));
      const comp = create();
      expect(comp.presentColumns()).toEqual(['AMOUNT_DEBITED']);
      expect(comp.missingColumns()).toEqual(['CURRENCY']);
      expect(comp.enrichmentNeeded()).toBe(true);
      expect(comp.hasAmounts()).toBe(true);
    });

    it('enrichmentNeeded est faux quand tout est présent', () => {
      analytics.getFlowsColumnsStatus.mockReturnValue(of({ AMOUNT_DEBITED: true }));
      const comp = create();
      expect(comp.enrichmentNeeded()).toBe(false);
      expect(comp.missingColumns()).toEqual([]);
    });
  });

  it('onYearChange recharge avec la nouvelle année', () => {
    const comp = create();
    analytics.getFlowsSummary.mockClear();
    comp.onYearChange(2024);
    expect(comp.selectedYear()).toBe(2024);
    expect(analytics.getFlowsSummary).toHaveBeenCalledWith(2024);
  });
});
