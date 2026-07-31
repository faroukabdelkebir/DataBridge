import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

import { Analytics } from './analytics';
import { AnalyticsService } from '../../core/services/analytics.service';
import { ThemeService } from '../../core/services/theme.service';
import { AnalyticsSummary, Insight } from '../../core/models/analytics.model';

function summary(o: Partial<AnalyticsSummary> = {}): AnalyticsSummary {
  return {
    totalClients: 100, nbVirements: 50, deltaVirementsPct: 5,
    nbFactures: 30, deltaFacturesPct: -2, nbGestionnaires: 4, ageMoyen: 42.34,
    ...(o as object),
  } as AnalyticsSummary;
}

describe('Analytics (composant)', () => {
  let analytics: Record<'getYears' | 'getSummary' | 'getMonthlyTransfers' | 'getAgePyramid' | 'getTopOfficers' | 'getBillingAging' | 'getInsights', ReturnType<typeof vi.fn>>;

  function create() {
    TestBed.configureTestingModule({
      providers: [
        Analytics,
        { provide: AnalyticsService, useValue: analytics },
        { provide: ThemeService, useValue: { darkMode: signal(false) } },
        { provide: TranslateService, useValue: { instant: (k: string) => k, currentLang: () => 'fr' } },
      ],
    });
    return TestBed.inject(Analytics);
  }

  beforeEach(() => {
    analytics = {
      getYears: vi.fn().mockReturnValue(of([2025, 2024])),
      getSummary: vi.fn().mockReturnValue(of(summary())),
      getMonthlyTransfers: vi.fn().mockReturnValue(of([])),
      getAgePyramid: vi.fn().mockReturnValue(of([])),
      getTopOfficers: vi.fn().mockReturnValue(of([])),
      getBillingAging: vi.fn().mockReturnValue(of([])),
      getInsights: vi.fn().mockReturnValue(of([])),
    };
  });

  describe('initialisation', () => {
    it('pré-sélectionne l’année la plus récente et charge', () => {
      const comp = create();
      expect(comp.years()).toEqual([2025, 2024]);
      expect(comp.selectedYear()).toBe(2025);
      expect(analytics.getSummary).toHaveBeenCalledWith(2025);
      expect(comp.loading()).toBe(false);
    });

    it('charge malgré l’échec de getYears', () => {
      analytics.getYears.mockReturnValue(throwError(() => new Error('x')));
      const comp = create();
      expect(analytics.getSummary).toHaveBeenCalledWith(0);
      expect(comp.loading()).toBe(false);
    });

    it('positionne error si le forkJoin échoue', () => {
      analytics.getSummary.mockReturnValue(throwError(() => new Error('x')));
      const comp = create();
      expect(comp.error()).toBe(true);
    });
  });

  describe('onYearChange', () => {
    it('met à jour l’année et recharge', () => {
      const comp = create();
      analytics.getSummary.mockClear();
      comp.onYearChange(2024);
      expect(comp.selectedYear()).toBe(2024);
      expect(analytics.getSummary).toHaveBeenCalledWith(2024);
    });
  });

  describe('cards (KPI)', () => {
    it('vide sans summary', () => {
      analytics.getSummary.mockReturnValue(throwError(() => new Error('x')));
      const comp = create();
      expect(comp.cards()).toEqual([]);
    });

    it('inclut la carte âge arrondie quand ageMoyen est défini', () => {
      const comp = create();
      const cards = comp.cards();
      expect(cards.map((c) => c.key)).toEqual(['clients', 'transfers', 'bills', 'officers', 'age']);
      expect(cards.find((c) => c.key === 'age')?.value).toBe(42.3);
      expect(cards.find((c) => c.key === 'transfers')?.delta).toBe(5);
    });

    it('omet la carte âge quand ageMoyen est null', () => {
      analytics.getSummary.mockReturnValue(of(summary({ ageMoyen: null })));
      const comp = create();
      expect(comp.cards().some((c) => c.key === 'age')).toBe(false);
    });
  });

  describe('insights', () => {
    const insight = (type: Insight['type']): Insight =>
      ({ type, titleKey: 'k', value: 'v', detail: 'd' });

    it('sépare le leader des autres insights', () => {
      analytics.getInsights.mockReturnValue(of([
        insight('LEADER'), insight('CONCENTRATION'), insight('PIC_ACTIVITE'),
      ]));
      const comp = create();
      expect(comp.leaderInsight()?.type).toBe('LEADER');
      expect(comp.otherInsights()).toHaveLength(2);
    });

    it('leaderInsight est null en l’absence de leader', () => {
      analytics.getInsights.mockReturnValue(of([insight('CONCENTRATION')]));
      const comp = create();
      expect(comp.leaderInsight()).toBeNull();
    });
  });
});
