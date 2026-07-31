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
  BillingSummary, FlowSlice, Insight, MonthlyFlow,
} from '../../core/models/analytics.model';

/** Palette catégorielle « banque » — lisible sur les deux thèmes. */
const SERIES = ['#2d5bd1', '#7aa5f8', '#1fa08e', '#e8a13c', '#8b5cf6', '#94a3b8'];

@Component({
  selector: 'app-billing-analytics',
  imports: [
    KpiCard, AnalyticsTabs, NgxEchartsDirective, TranslatePipe, FormsModule,
    TableModule, ButtonModule, SelectModule, SelectButtonModule, TooltipModule,
    RouterLink, DecimalPipe,
  ],
  templateUrl: './billing.html',
  styleUrl: './billing.scss',
})
export class BillingAnalytics {
  private analytics = inject(AnalyticsService);
  private theme = inject(ThemeService);
  private translate = inject(TranslateService);

  // ── État ───────────────────────────────────────────────────
  readonly summary = signal<BillingSummary | null>(null);
  readonly monthly = signal<MonthlyFlow[]>([]);
  readonly breakdown = signal<FlowSlice[]>([]);
  readonly insights = signal<Insight[]>([]);
  readonly columnsStatus = signal<Record<string, boolean>>({});
  readonly dimensions = signal<string[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);

  // ── Filtres : année + dimension de segmentation ────────────
  readonly years = signal<number[]>([]);
  readonly selectedYear = signal<number>(0);
  readonly selectedDimension = signal<string>('aging');

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
      label: this.translate.instant(`analytics.billing.dimension.${d}`),
      value: d,
    }));
  });

  constructor() {
    this.analytics.getBillYears().subscribe({
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
      summary: this.analytics.getBillingSummary(year),
      monthly: this.analytics.getBillingMonthly(year),
      insights: this.analytics.getBillingInsights(year),
      columns: this.analytics.getBillingColumnsStatus(),
      dimensions: this.analytics.getBillingDimensions(),
    }).subscribe({
      next: (r) => {
        this.summary.set(r.summary);
        this.monthly.set(r.monthly);
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
      ? this.analytics.getBillingBreakdown(dim, this.selectedYear())
      : of([] as FlowSlice[])
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
  readonly hasAmounts = computed(() =>
    this.columnsStatus()['OS_TOTAL_AMOUNT'] === true);

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
      { key: 'bills', label: 'analytics.billing.kpi.bills', value: s.nbFactures, format: 'number', delta: s.deltaFacturesPct, icon: 'pi pi-receipt', color: '#2d5bd1' },
    ];
    if (s.montantFacture !== null) {
      cards.push({ key: 'billed', label: 'analytics.billing.kpi.billed', value: s.montantFacture, format: 'number', delta: null, icon: 'pi pi-file-check', color: '#1fa08e' });
    }
    if (s.encoursTotal !== null) {
      cards.push({ key: 'outstanding', label: 'analytics.billing.kpi.outstanding', value: s.encoursTotal, format: 'number', delta: null, icon: 'pi pi-hourglass', color: '#e8a13c' });
    }
    if (s.pctPayees !== null) {
      cards.push({ key: 'paid', label: 'analytics.billing.kpi.paidPct', value: s.pctPayees, format: 'number', delta: null, icon: 'pi pi-check-circle', color: '#8b5cf6' });
    }
    if (s.delaiMoyenReglement !== null) {
      cards.push({ key: 'delay', label: 'analytics.billing.kpi.delay', value: s.delaiMoyenReglement, format: 'number', delta: null, icon: 'pi pi-clock', color: '#7aa5f8' });
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

  // ── Graphique 1 : factures par mois N vs N-1 (+ montants) ──
  readonly monthlyOptions = computed<EChartsCoreOption>(() => {
    const c = this.palette();
    const data = this.monthly();
    const hasN1 = data.some((p) => p.nbOperationsN1 !== null);
    const hasAmount = data.some((p) => p.montantTotal !== null);
    const nLabel = this.t('analytics.chart.currentYear');
    const n1Label = this.t('analytics.chart.previousYear');
    const amountLabel = this.t('analytics.billing.chart.amountLine');

    const legend: string[] = [nLabel];
    const series: object[] = [];

    if (hasN1) {
      legend.push(n1Label);
      series.push({
        name: n1Label, type: 'bar', barGap: '-40%', z: 1,
        itemStyle: { color: c.ghost, borderRadius: [4, 4, 0, 0] },
        data: data.map((p) => p.nbOperationsN1 ?? 0),
      });
    }
    series.push({
      name: nLabel, type: 'bar', z: 2,
      itemStyle: { color: SERIES[0], borderRadius: [4, 4, 0, 0] },
      data: data.map((p) => p.nbOperations),
    });
    if (hasAmount) {
      legend.push(amountLabel);
      series.push({
        name: amountLabel, type: 'line', yAxisIndex: 1, z: 3, smooth: true,
        symbolSize: 7, lineStyle: { width: 2.5 },
        itemStyle: { color: SERIES[3] },
        data: data.map((p) => p.montantTotal ?? 0),
      });
    }

    const yAxis: object[] = [{
      type: 'value',
      axisLabel: { color: c.text }, splitLine: { lineStyle: { color: c.split } },
    }];
    if (hasAmount) {
      yAxis.push({
        type: 'value',
        axisLabel: { color: c.text }, splitLine: { show: false },
      });
    }

    return {
      textStyle: { color: c.text },
      tooltip: { trigger: 'axis' },
      legend: { data: legend, bottom: 0, textStyle: { color: c.text } },
      grid: { left: 55, right: hasAmount ? 65 : 20, top: 30, bottom: 40 },
      xAxis: {
        type: 'category', data: this.monthLabels(),
        axisLabel: { color: c.text }, axisLine: { lineStyle: { color: c.split } },
      },
      yAxis,
      series,
    };
  });

  // ── Graphique 2 : jauge du taux de recouvrement ────────────
  readonly recoveryOptions = computed<EChartsCoreOption>(() => {
    const c = this.palette();
    const value = this.summary()?.tauxRecouvrement ?? 0;
    const color = value >= 80 ? SERIES[2] : value >= 50 ? SERIES[3] : '#ef4444';
    return {
      series: [{
        type: 'gauge',
        startAngle: 210, endAngle: -30,
        min: 0, max: 100,
        progress: { show: true, width: 16, itemStyle: { color } },
        axisLine: { lineStyle: { width: 16, color: [[1, c.ghost]] } },
        pointer: { show: false },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        title: { show: false },
        detail: {
          formatter: '{value} %',
          color: c.text, fontSize: 30, fontWeight: 700,
          offsetCenter: [0, '5%'],
          valueAnimation: true,
        },
        data: [{ value: Math.round(value * 10) / 10 }],
      }],
    };
  });

  /** Traduit une valeur métier via analytics.value.*, sinon la laisse brute. */
  dataLabel(v: string): string {
    if (!v) return v;
    const key = 'analytics.value.' + v;
    const t = this.translate.instant(key);
    return t === key ? v : t;
  }

  // ── Graphique 3 : segmentation (dimension commutable) ──────
  readonly breakdownOptions = computed<EChartsCoreOption>(() => {
    const c = this.palette();
    this.translate.currentLang();                    // réactif au changement de langue
    const rows = [...this.breakdown()].reverse();   // la 1ʳᵉ catégorie en haut
    return {
      textStyle: { color: c.text },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 120, right: 70, top: 10, bottom: 20 },
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
        name: this.t('analytics.billing.chart.bills'),
        type: 'bar',
        itemStyle: { color: SERIES[3], borderRadius: [0, 4, 4, 0] },
        label: {
          show: true, position: 'right', color: c.text, fontSize: 11,
          formatter: (p: { dataIndex: number }) =>
            `${rows[p.dataIndex].nbOperations} (${rows[p.dataIndex].pct} %)`,
        },
        data: rows.map((s) => s.nbOperations),
      }],
    };
  });
}
