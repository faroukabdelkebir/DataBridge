import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { NgxEchartsDirective } from 'ngx-echarts';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import type { EChartsCoreOption } from 'echarts/core';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TooltipModule } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { KpiCard } from '../../shared/components/kpi-card/kpi-card';
import { AnalyticsTabs } from './analytics-tabs';
import { AnalyticsService } from '../../core/services/analytics.service';
import { ThemeService } from '../../core/services/theme.service';
import { Kpi } from '../../core/models/kpi.model';
import {
  AnalyticsSummary, BreakdownSlice, Insight, MonthlyFlow, OfficerPerf,
} from '../../core/models/analytics.model';

/** Palette catégorielle « banque » — lisible sur les deux thèmes. */
const SERIES = ['#2d5bd1', '#7aa5f8', '#1fa08e', '#e8a13c', '#8b5cf6', '#94a3b8'];

@Component({
  selector: 'app-analytics',
  imports: [
    KpiCard, AnalyticsTabs, NgxEchartsDirective, TranslatePipe, FormsModule,
    TableModule, ButtonModule, SelectModule, TooltipModule, DecimalPipe,
  ],
  templateUrl: './analytics.html',
  styleUrl: './analytics.scss',
})
export class Analytics {
  private analytics = inject(AnalyticsService);
  private theme = inject(ThemeService);
  private translate = inject(TranslateService);
  private destroyRef = inject(DestroyRef);

  // ── État ───────────────────────────────────────────────────
  readonly summary = signal<AnalyticsSummary | null>(null);
  readonly monthly = signal<MonthlyFlow[]>([]);
  readonly pyramid = signal<BreakdownSlice[]>([]);
  readonly officers = signal<OfficerPerf[]>([]);
  readonly aging = signal<BreakdownSlice[]>([]);
  readonly insights = signal<Insight[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);

  // ── Filtre global : année ──────────────────────────────────
  readonly years = signal<number[]>([]);
  readonly selectedYear = signal<number>(0);        // 0 = tout l'historique
  readonly yearOptions = computed(() => {
    this.translate.currentLang();
    return [
      { label: this.translate.instant('analytics.filter.allYears'), value: 0 },
      ...this.years().map((y) => ({ label: String(y), value: y })),
    ];
  });

  constructor() {
    this.analytics.getYears().subscribe({
      next: (ys) => {
        this.years.set(ys);
        // Pré-sélectionne l'année la plus récente : la page ouvre sur du concret.
        if (ys.length > 0) this.selectedYear.set(ys[0]);
        this.load();
      },
      error: () => this.load(),
    });
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    const year = this.selectedYear();

    forkJoin({
      summary: this.analytics.getSummary(year),
      monthly: this.analytics.getMonthlyTransfers(year),
      pyramid: this.analytics.getAgePyramid(),
      officers: this.analytics.getTopOfficers(10),
      aging: this.analytics.getBillingAging(),
      insights: this.analytics.getInsights(year),
    }).subscribe({
      next: (r) => {
        this.summary.set(r.summary);
        this.monthly.set(r.monthly);
        this.pyramid.set(r.pyramid);
        this.officers.set(r.officers);
        this.aging.set(r.aging);
        this.insights.set(r.insights);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });
  }

  onYearChange(year: number): void {
    this.selectedYear.set(year);
    this.load();
  }

  // ── Insight « leader » mis en avant (élément signature) ────
  readonly leaderInsight = computed(() =>
    this.insights().find((i) => i.type === 'LEADER') ?? null);
  readonly otherInsights = computed(() =>
    this.insights().filter((i) => i.type !== 'LEADER'));

  // ── Cartes KPI (réutilise app-kpi-card) ────────────────────
  readonly cards = computed<Kpi[]>(() => {
    const s = this.summary();
    if (!s) return [];
    const cards: Kpi[] = [
      { key: 'clients',   label: 'analytics.kpi.clients',   value: s.totalClients,    format: 'number', delta: null,                  icon: 'pi pi-users',       color: '#2d5bd1' },
      { key: 'transfers', label: 'analytics.kpi.transfers', value: s.nbVirements,     format: 'number', delta: s.deltaVirementsPct,   icon: 'pi pi-arrows-h',    color: '#1fa08e' },
      { key: 'bills',     label: 'analytics.kpi.bills',     value: s.nbFactures,      format: 'number', delta: s.deltaFacturesPct,    icon: 'pi pi-receipt',     color: '#e8a13c' },
      { key: 'officers',  label: 'analytics.kpi.officers',  value: s.nbGestionnaires, format: 'number', delta: null,                  icon: 'pi pi-briefcase',   color: '#8b5cf6' },
    ];
    if (s.ageMoyen !== null) {
      cards.push({ key: 'age', label: 'analytics.kpi.avgAge', value: Math.round(s.ageMoyen * 10) / 10, format: 'number', delta: null, icon: 'pi pi-id-card', color: '#7aa5f8' });
    }
    return cards;
  });

  // ── Palette réactive au thème ──────────────────────────────
  private readonly palette = computed(() => {
    const dark = this.theme.darkMode();
    return {
      text: dark ? '#9aa4b2' : '#6b7280',
      split: dark ? '#2a333f' : '#eef1f6',
      surface: dark ? '#1a212b' : '#ffffff',
      ghost: dark ? '#3b4656' : '#dfe5f0',      // série N-1, volontairement en retrait
    };
  });

  private t(key: string): string {
    this.translate.currentLang();
    return this.translate.instant(key);
  }

  private monthLabels(): string[] {
    this.translate.currentLang();
    return Array.from({ length: 12 }, (_, i) =>
      this.translate.instant(`analytics.months.${i + 1}`));
  }

  // ── Graphique 1 : évolution mensuelle N vs N-1 ─────────────
  readonly monthlyOptions = computed<EChartsCoreOption>(() => {
    const c = this.palette();
    const data = this.monthly();
    const hasN1 = data.some((p) => p.nbOperationsN1 !== null);
    const nLabel = this.t('analytics.chart.currentYear');
    const n1Label = this.t('analytics.chart.previousYear');

    const series: object[] = [];
    if (hasN1) {
      series.push({
        name: n1Label, type: 'bar', barGap: '-40%', z: 1,
        itemStyle: { color: c.ghost, borderRadius: [4, 4, 0, 0] },
        data: data.map((p) => p.nbOperationsN1 ?? 0),
      });
    }
    series.push({
      name: nLabel, type: 'bar', z: 2,
      itemStyle: { color: SERIES[0], borderRadius: [4, 4, 0, 0] },
      label: { show: true, position: 'top', color: c.text, fontSize: 10 },
      data: data.map((p) => p.nbOperations),
    });

    return {
      textStyle: { color: c.text },
      tooltip: { trigger: 'axis' },
      legend: { data: hasN1 ? [nLabel, n1Label] : [nLabel], bottom: 0, textStyle: { color: c.text } },
      grid: { left: 50, right: 20, top: 30, bottom: 40 },
      xAxis: {
        type: 'category', data: this.monthLabels(),
        axisLabel: { color: c.text }, axisLine: { lineStyle: { color: c.split } },
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: c.text }, splitLine: { lineStyle: { color: c.split } },
      },
      series,
    };
  });

  // ── Graphique 2 : répartition par tranche d'âge ────────────
  readonly pyramidOptions = computed<EChartsCoreOption>(() => {
    const c = this.palette();
    return {
      tooltip: { trigger: 'item', formatter: '{b} : {c} ({d}%)' },
      legend: { bottom: 0, textStyle: { color: c.text } },
      series: [{
        name: this.t('analytics.chart.agePyramid'),
        type: 'pie',
        radius: ['48%', '72%'],
        itemStyle: { borderRadius: 6, borderColor: c.surface, borderWidth: 2 },
        label: { show: false },
        data: this.pyramid().map((s) => ({ name: s.label, value: s.value })),
        color: SERIES,
      }],
    };
  });

  // ── Graphique 3 : top gestionnaires (barres horizontales) ──
  readonly officersOptions = computed<EChartsCoreOption>(() => {
    const c = this.palette();
    const rows = [...this.officers()].reverse();     // le leader en haut
    return {
      textStyle: { color: c.text },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 70, right: 60, top: 10, bottom: 20 },
      xAxis: {
        type: 'value',
        axisLabel: { color: c.text }, splitLine: { lineStyle: { color: c.split } },
      },
      yAxis: {
        type: 'category',
        data: rows.map((o) => o.officer),
        axisLabel: { color: c.text }, axisLine: { lineStyle: { color: c.split } },
      },
      series: [{
        name: this.t('analytics.chart.clients'),
        type: 'bar',
        itemStyle: { color: SERIES[0], borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: 'right', color: c.text, fontSize: 11 },
        data: rows.map((o) => o.nbClients),
      }],
    };
  });
}
