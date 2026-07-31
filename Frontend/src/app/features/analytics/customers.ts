import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { NgxEchartsDirective } from 'ngx-echarts';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import type { EChartsCoreOption } from 'echarts/core';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TooltipModule } from 'primeng/tooltip';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';

import { KpiCard } from '../../shared/components/kpi-card/kpi-card';
import { AnalyticsTabs } from './analytics-tabs';
import { AnalyticsService } from '../../core/services/analytics.service';
import { ThemeService } from '../../core/services/theme.service';
import { Kpi } from '../../core/models/kpi.model';
import {
  BreakdownSlice, CustomersSummary, Insight, MonthlyFlow,
} from '../../core/models/analytics.model';

/** Palette catégorielle « banque » — lisible sur les deux thèmes. */
const SERIES = ['#2d5bd1', '#7aa5f8', '#1fa08e', '#e8a13c', '#8b5cf6', '#94a3b8'];

@Component({
  selector: 'app-customers-analytics',
  imports: [
    KpiCard, AnalyticsTabs, NgxEchartsDirective, TranslatePipe, FormsModule,
    TableModule, ButtonModule, SelectModule, SelectButtonModule, TooltipModule,
    RouterLink, DecimalPipe,
  ],
  templateUrl: './customers.html',
  styleUrl: './customers.scss',
})
export class CustomersAnalytics {
  private analytics = inject(AnalyticsService);
  private theme = inject(ThemeService);
  private translate = inject(TranslateService);

  // ── État ───────────────────────────────────────────────────
  readonly summary = signal<CustomersSummary | null>(null);
  readonly histogram = signal<BreakdownSlice[]>([]);
  readonly generations = signal<BreakdownSlice[]>([]);
  readonly newMonthly = signal<MonthlyFlow[]>([]);
  readonly breakdown = signal<BreakdownSlice[]>([]);
  readonly insights = signal<Insight[]>([]);
  readonly columnsStatus = signal<Record<string, boolean>>({});
  readonly dimensions = signal<string[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);

  // ── Filtres : année d'acquisition + dimension de segmentation ─
  readonly years = signal<number[]>([]);
  readonly selectedYear = signal<number>(0);
  readonly selectedDimension = signal<string>('sector');

  readonly yearOptions = computed(() => {
    this.translate.currentLang();
    return [
      { label: this.translate.instant('analytics.filter.allYears'), value: 0 },
      ...this.years().map((y) => ({ label: String(y), value: y })),
    ];
  });

  readonly dimensionOptions = computed(() => {
    this.translate.currentLang();
    return this.dimensions().map((d) => ({
      label: this.translate.instant(`analytics.customers.dimension.${d}`),
      value: d,
    }));
  });

  constructor() {
    this.analytics.getAcquisitionYears().subscribe({
      next: (ys) => {
        this.years.set(ys);
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
      summary: this.analytics.getCustomersSummary(year),
      histogram: this.analytics.getAgeHistogram(),
      generations: this.analytics.getGenerations(),
      newMonthly: this.analytics.getNewCustomersMonthly(year),
      insights: this.analytics.getCustomersInsights(year),
      columns: this.analytics.getCustomersColumnsStatus(),
      dimensions: this.analytics.getCustomerDimensions(),
    }).subscribe({
      next: (r) => {
        this.summary.set(r.summary);
        this.histogram.set(r.histogram);
        this.generations.set(r.generations);
        this.newMonthly.set(r.newMonthly);
        this.insights.set(r.insights);
        this.columnsStatus.set(r.columns);
        this.dimensions.set(r.dimensions);
        this.loading.set(false);

        if (r.dimensions.length > 0) {
          if (!r.dimensions.includes(this.selectedDimension())) {
            this.selectedDimension.set(r.dimensions[0]);
          }
          this.loadBreakdown();
        }
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });
  }

  loadBreakdown(): void {
    const dim = this.selectedDimension();
    (this.dimensions().includes(dim)
      ? this.analytics.getCustomersBreakdown(dim)
      : of([] as BreakdownSlice[])
    ).subscribe({
      next: (slices) => this.breakdown.set(slices),
      error: () => this.breakdown.set([]),
    });
  }

  onYearChange(year: number): void {
    this.selectedYear.set(year);
    this.load();
  }

  onDimensionChange(dim: string): void {
    this.selectedDimension.set(dim);
    this.loadBreakdown();
  }

  // ── Panneau « à débloquer » ────────────────────────────────
  readonly missingColumns = computed(() =>
    Object.entries(this.columnsStatus())
      .filter(([, present]) => !present)
      .map(([col]) => col));
  readonly presentColumns = computed(() =>
    Object.entries(this.columnsStatus())
      .filter(([, present]) => present)
      .map(([col]) => col));
  readonly enrichmentNeeded = computed(() => this.missingColumns().length > 0);
  readonly hasAcquisition = computed(() =>
    this.columnsStatus()['DATE_AC_OPND'] === true);

  // ── Insights ───────────────────────────────────────────────
  readonly leaderInsight = computed(() =>
    this.insights().find((i) => i.type === 'LEADER') ?? null);
  readonly otherInsights = computed(() =>
    this.insights().filter((i) => i.type !== 'LEADER'));

  // ── Cartes KPI ─────────────────────────────────────────────
  readonly cards = computed<Kpi[]>(() => {
    const s = this.summary();
    if (!s) return [];
    const cards: Kpi[] = [
      { key: 'clients', label: 'analytics.customers.kpi.clients', value: s.totalClients, format: 'number', delta: null, icon: 'pi pi-users', color: '#2d5bd1' },
    ];
    if (s.ageMoyen !== null) {
      cards.push({ key: 'age', label: 'analytics.customers.kpi.avgAge', value: s.ageMoyen, format: 'number', delta: null, icon: 'pi pi-id-card', color: '#7aa5f8' });
    }
    if (s.nouveauxClients !== null) {
      cards.push({ key: 'new', label: 'analytics.customers.kpi.newCustomers', value: s.nouveauxClients, format: 'number', delta: s.deltaNouveauxPct, icon: 'pi pi-user-plus', color: '#1fa08e' });
    }
    if (s.pctFemmes !== null) {
      cards.push({ key: 'women', label: 'analytics.customers.kpi.womenPct', value: s.pctFemmes, format: 'number', delta: null, icon: 'pi pi-venus', color: '#8b5cf6' });
    }
    if (s.nbNationalites !== null) {
      cards.push({ key: 'nat', label: 'analytics.customers.kpi.nationalities', value: s.nbNationalites, format: 'number', delta: null, icon: 'pi pi-globe', color: '#e8a13c' });
    } else {
      cards.push({ key: 'officers', label: 'analytics.customers.kpi.officers', value: s.nbGestionnaires, format: 'number', delta: null, icon: 'pi pi-briefcase', color: '#e8a13c' });
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
      ghost: dark ? '#3b4656' : '#dfe5f0',
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

  // ── Graphique 1 : histogramme des âges (pas de 5 ans) ──────
  readonly histogramOptions = computed<EChartsCoreOption>(() => {
    const c = this.palette();
    const data = this.histogram();
    return {
      textStyle: { color: c.text },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 50, right: 20, top: 25, bottom: 30 },
      xAxis: {
        type: 'category', data: data.map((s) => s.label),
        axisLabel: { color: c.text, interval: 0, rotate: 40, fontSize: 10 },
        axisLine: { lineStyle: { color: c.split } },
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: c.text }, splitLine: { lineStyle: { color: c.split } },
      },
      series: [{
        name: this.t('analytics.customers.chart.clients'),
        type: 'bar',
        itemStyle: { color: SERIES[0], borderRadius: [4, 4, 0, 0] },
        data: data.map((s) => s.value),
      }],
    };
  });

  // ── Graphique 2 : donut générations ────────────────────────
  readonly generationsOptions = computed<EChartsCoreOption>(() => {
    const c = this.palette();
    return {
      tooltip: { trigger: 'item', formatter: '{b} : {c} ({d}%)' },
      legend: { bottom: 0, textStyle: { color: c.text } },
      series: [{
        name: this.t('analytics.customers.chart.generationsTitle'),
        type: 'pie',
        radius: ['48%', '72%'],
        itemStyle: { borderRadius: 6, borderColor: c.surface, borderWidth: 2 },
        label: { show: false },
        data: this.generations().map((s) => ({ name: s.label, value: s.value })),
        color: SERIES,
      }],
    };
  });

  // ── Graphique 3 : nouveaux clients par mois N vs N-1 ───────
  readonly acquisitionOptions = computed<EChartsCoreOption>(() => {
    const c = this.palette();
    const data = this.newMonthly();
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
      itemStyle: { color: SERIES[2], borderRadius: [4, 4, 0, 0] },
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

  /** Traduit une valeur métier via analytics.value.*, sinon la laisse brute. */
  dataLabel(v: string): string {
    if (!v) return v;
    const key = 'analytics.value.' + v;
    const t = this.translate.instant(key);
    return t === key ? v : t;
  }

  // ── Graphique 4 : segmentation (dimension commutable) ──────
  readonly breakdownOptions = computed<EChartsCoreOption>(() => {
    const c = this.palette();
    this.translate.currentLang();                    // réactif au changement de langue
    const rows = [...this.breakdown()].reverse();   // la 1ʳᵉ catégorie en haut
    return {
      textStyle: { color: c.text },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 110, right: 60, top: 10, bottom: 20 },
      xAxis: {
        type: 'value',
        axisLabel: { color: c.text }, splitLine: { lineStyle: { color: c.split } },
      },
      yAxis: {
        type: 'category',
        data: rows.map((s) => this.dataLabel(s.label)),
        axisLabel: { color: c.text, fontSize: 11 },
        axisLine: { lineStyle: { color: c.split } },
      },
      series: [{
        name: this.t('analytics.customers.chart.clients'),
        type: 'bar',
        itemStyle: { color: SERIES[4], borderRadius: [0, 4, 4, 0] },
        label: {
          show: true, position: 'right', color: c.text, fontSize: 11,
          formatter: (p: { dataIndex: number }) =>
            `${rows[p.dataIndex].value} (${rows[p.dataIndex].pct} %)`,
        },
        data: rows.map((s) => s.value),
      }],
    };
  });
}
