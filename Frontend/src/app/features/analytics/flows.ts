import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { NgxEchartsDirective } from 'ngx-echarts';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import type { EChartsCoreOption } from 'echarts/core';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TooltipModule } from 'primeng/tooltip';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { KpiCard } from '../../shared/components/kpi-card/kpi-card';
import { AnalyticsTabs } from './analytics-tabs';
import { AnalyticsService } from '../../core/services/analytics.service';
import { ThemeService } from '../../core/services/theme.service';
import { Kpi } from '../../core/models/kpi.model';
import {
  FlowSlice, FlowsSummary, HeatmapCell, Insight, MonthlyFlow,
} from '../../core/models/analytics.model';

/** Palette catégorielle « banque » — lisible sur les deux thèmes. */
const SERIES = ['#2d5bd1', '#7aa5f8', '#1fa08e', '#e8a13c', '#8b5cf6', '#94a3b8'];

@Component({
  selector: 'app-flows',
  imports: [
    KpiCard, AnalyticsTabs, NgxEchartsDirective, TranslatePipe, FormsModule,
    TableModule, ButtonModule, SelectModule, TooltipModule, RouterLink, DecimalPipe,
  ],
  templateUrl: './flows.html',
  styleUrl: './flows.scss',
})
export class Flows {
  private analytics = inject(AnalyticsService);
  private theme = inject(ThemeService);
  private translate = inject(TranslateService);

  // ── État ───────────────────────────────────────────────────
  readonly summary = signal<FlowsSummary | null>(null);
  readonly monthly = signal<MonthlyFlow[]>([]);
  readonly byCurrency = signal<FlowSlice[]>([]);
  readonly byType = signal<FlowSlice[]>([]);
  readonly heatmap = signal<HeatmapCell[]>([]);
  readonly insights = signal<Insight[]>([]);
  readonly columnsStatus = signal<Record<string, boolean>>({});
  readonly loading = signal(true);
  readonly error = signal(false);

  // ── Filtre global : année ──────────────────────────────────
  readonly years = signal<number[]>([]);
  readonly selectedYear = signal<number>(0);
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
      summary: this.analytics.getFlowsSummary(year),
      monthly: this.analytics.getMonthlyTransfers(year),
      byCurrency: this.analytics.getFlowsByCurrency(year),
      byType: this.analytics.getFlowsByType(year),
      heatmap: this.analytics.getActivityHeatmap(year),
      insights: this.analytics.getFlowsInsights(year),
      columns: this.analytics.getFlowsColumnsStatus(),
    }).subscribe({
      next: (r) => {
        this.summary.set(r.summary);
        this.monthly.set(r.monthly);
        this.byCurrency.set(r.byCurrency);
        this.byType.set(r.byType);
        this.heatmap.set(r.heatmap);
        this.insights.set(r.insights);
        this.columnsStatus.set(r.columns);
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

  // ── Panneau « à débloquer » : colonnes enrichies manquantes ─
  readonly missingColumns = computed(() =>
    Object.entries(this.columnsStatus())
      .filter(([, present]) => !present)
      .map(([col]) => col));
  readonly presentColumns = computed(() =>
    Object.entries(this.columnsStatus())
      .filter(([, present]) => present)
      .map(([col]) => col));
  readonly enrichmentNeeded = computed(() => this.missingColumns().length > 0);
  readonly hasAmounts = computed(() => this.columnsStatus()['AMOUNT_DEBITED'] === true);

  // ── Insight leader + secondaires ───────────────────────────
  readonly leaderInsight = computed(() =>
    this.insights().find((i) => i.type === 'LEADER') ?? null);
  readonly otherInsights = computed(() =>
    this.insights().filter((i) => i.type !== 'LEADER'));

  // ── Cartes KPI ─────────────────────────────────────────────
  readonly cards = computed<Kpi[]>(() => {
    const s = this.summary();
    if (!s) return [];
    const cards: Kpi[] = [
      { key: 'ops', label: 'analytics.flows.kpi.transfers', value: s.nbOperations, format: 'number', delta: s.deltaOpsPct, icon: 'pi pi-arrows-h', color: '#2d5bd1' },
    ];
    if (s.montantTotal !== null) {
      cards.push({ key: 'amount', label: 'analytics.flows.kpi.amount', value: s.montantTotal, format: 'number', delta: s.deltaMontantPct, icon: 'pi pi-wallet', color: '#1fa08e' });
    }
    if (s.ticketMoyen !== null) {
      cards.push({ key: 'ticket', label: 'analytics.flows.kpi.avgTicket', value: s.ticketMoyen, format: 'number', delta: null, icon: 'pi pi-calculator', color: '#e8a13c' });
    }
    if (s.nbDevises !== null) {
      cards.push({ key: 'currencies', label: 'analytics.flows.kpi.currencies', value: s.nbDevises, format: 'number', delta: null, icon: 'pi pi-globe', color: '#8b5cf6' });
    }
    if (s.nbTypes !== null) {
      cards.push({ key: 'types', label: 'analytics.flows.kpi.types', value: s.nbTypes, format: 'number', delta: null, icon: 'pi pi-tags', color: '#7aa5f8' });
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
      heatLow: dark ? '#232c38' : '#eef2fb',
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

  private dayLabels(): string[] {
    this.translate.currentLang();
    return Array.from({ length: 7 }, (_, i) =>
      this.translate.instant(`analytics.days.${i}`));
  }

  // ── Graphique 1 : combo volumes (barres) + montants (ligne) ─
  readonly monthlyOptions = computed<EChartsCoreOption>(() => {
    const c = this.palette();
    const data = this.monthly();
    const hasN1 = data.some((p) => p.nbOperationsN1 !== null);
    const hasAmount = data.some((p) => p.montantTotal !== null);
    const nLabel = this.t('analytics.chart.currentYear');
    const n1Label = this.t('analytics.chart.previousYear');
    const amountLabel = this.t('analytics.flows.chart.amountLine');

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

  // ── Graphique 2 : répartition par devise ───────────────────
  readonly currencyOptions = computed<EChartsCoreOption>(() => {
    const c = this.palette();
    return {
      tooltip: { trigger: 'item', formatter: '{b} : {c} ({d}%)' },
      legend: { bottom: 0, textStyle: { color: c.text } },
      series: [{
        name: this.t('analytics.flows.chart.currencyTitle'),
        type: 'pie',
        radius: ['48%', '72%'],
        itemStyle: { borderRadius: 6, borderColor: c.surface, borderWidth: 2 },
        label: { show: false },
        data: this.byCurrency().map((s) => ({
          name: s.label,
          value: s.montantTotal ?? s.nbOperations,
        })),
        color: SERIES,
      }],
    };
  });

  // ── Graphique 3 : heatmap mois × jour de semaine ───────────
  readonly heatmapOptions = computed<EChartsCoreOption>(() => {
    const c = this.palette();
    const cells = this.heatmap();
    const max = Math.max(1, ...cells.map((h) => h.nb));
    return {
      textStyle: { color: c.text },
      tooltip: { position: 'top' },
      grid: { left: 55, right: 20, top: 10, bottom: 70 },
      xAxis: {
        type: 'category', data: this.monthLabels(),
        splitArea: { show: true },
        axisLabel: { color: c.text }, axisLine: { lineStyle: { color: c.split } },
      },
      yAxis: {
        type: 'category', data: this.dayLabels(),
        splitArea: { show: true },
        axisLabel: { color: c.text }, axisLine: { lineStyle: { color: c.split } },
      },
      visualMap: {
        min: 0, max,
        calculable: false, orient: 'horizontal',
        left: 'center', bottom: 0,
        textStyle: { color: c.text },
        inRange: { color: [c.heatLow, '#7aa5f8', '#2d5bd1'] },
      },
      series: [{
        name: this.t('analytics.flows.chart.heatmapTitle'),
        type: 'heatmap',
        data: cells.map((h) => [h.mois - 1, h.jourSemaine, h.nb]),
        label: { show: false },
        emphasis: { itemStyle: { shadowBlur: 6, shadowColor: 'rgba(0,0,0,.3)' } },
      }],
    };
  });
}
