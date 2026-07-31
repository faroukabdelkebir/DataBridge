import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, NgClass } from '@angular/common';
import { NgxEchartsDirective } from 'ngx-echarts';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import type { EChartsCoreOption } from 'echarts/core';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { forkJoin } from 'rxjs';

import { KpiCard } from '../../shared/components/kpi-card/kpi-card';
import { DurationPipe } from '../../shared/pipes/duration.pipe';
import { MonitoringService } from '../../core/services/monitoring.service';
import { JobStreamService } from '../../core/services/job-stream.service';
import { ThemeService } from '../../core/services/theme.service';
import { Kpi as KpiCardModel } from '../../core/models/kpi.model';
import {
  DashboardSummary,
  JobExecution,
  Kpi,
} from '../../core/models/monitoring.model';

@Component({
  selector: 'app-dashboard',
  imports: [
    KpiCard, NgxEchartsDirective, TranslatePipe,
    TableModule, TagModule, ButtonModule, TooltipModule,
    DatePipe, DecimalPipe, DurationPipe, NgClass,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private monitoring = inject(MonitoringService);
  private stream = inject(JobStreamService);
  private theme = inject(ThemeService);
  private translate = inject(TranslateService);
  private destroyRef = inject(DestroyRef);

  // ── État ───────────────────────────────────────────────────
  readonly summary = signal<DashboardSummary | null>(null);
  readonly kpiData = signal<Kpi | null>(null);
  readonly recentJobs = signal<JobExecution[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);

  // SSE (avec repli sur le summary tant que le flux n'a rien émis)
  readonly streamConnected = this.stream.connected;
  readonly liveJobs = computed<JobExecution[]>(() => {
    const fromStream = this.stream.liveJobs();
    return fromStream.length > 0 ? fromStream : (this.summary()?.liveJobs ?? []);
  });
  readonly activeAlerts = computed(() => {
    const fromStream = this.stream.alerts();
    return fromStream.length > 0 ? fromStream : (this.summary()?.activeAlerts ?? []);
  });

  constructor() {
    this.load();
    this.stream.connect();
    this.destroyRef.onDestroy(() => this.stream.disconnect());
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);

    forkJoin({
      summary: this.monitoring.getSummary(),
      kpis: this.monitoring.getKpis(7),
      recent: this.monitoring.getRecentJobs(16),
    }).subscribe({
      next: ({ summary, kpis, recent }) => {
        this.summary.set(summary);
        this.kpiData.set(kpis);
        // Source hydratée (compteurs read/write/skip renseignés), triée par début décroissant
        this.recentJobs.set(
          [...recent].sort((a, b) => (b.startTime ?? '').localeCompare(a.startTime ?? '')),
        );
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });
  }

  // ── Cartes KPI (réutilise app-kpi-card) ────────────────────
  readonly cards = computed<KpiCardModel[]>(() => {
    const s = this.summary();
    if (!s) return [];
    return [
      { key: 'running',   label: 'dashboard.kpi.running',   value: s.runningJobsCount,   format: 'number',  delta: null, icon: 'pi pi-sync',         color: '#6366f1' },
      { key: 'completed', label: 'dashboard.kpi.completed', value: s.completedTodayCount, format: 'number',  delta: null, icon: 'pi pi-check-circle', color: '#22c55e' },
      { key: 'failed',    label: 'dashboard.kpi.failed',    value: s.failedTodayCount,    format: 'number',  delta: null, icon: 'pi pi-times-circle', color: '#ef4444' },
      { key: 'success7d', label: 'dashboard.kpi.success7d', value: s.successRate7d,       format: 'percent', delta: null, icon: 'pi pi-percentage',   color: '#06b6d4' },
    ];
  });

  // ── Palette réactive au thème ──────────────────────────────
  private readonly palette = computed(() => {
    const dark = this.theme.darkMode();
    return {
      text: dark ? '#9aa4b2' : '#6b7280',
      split: dark ? '#2a333f' : '#eef1f6',
      surface: dark ? '#1a212b' : '#ffffff',
    };
  });

  private t(key: string): string {
    this.translate.currentLang();
    return this.translate.instant(key);
  }

  // ── Graphique 1 : tendance des volumes (volumeTrend) ───────
  readonly trendOptions = computed<EChartsCoreOption>(() => {
    const c = this.palette();
    const trend = this.kpiData()?.volumeTrend ?? [];
    const readLabel = this.t('dashboard.chart.rowsRead');
    const writeLabel = this.t('dashboard.chart.rowsWritten');
    const rateLabel = this.t('dashboard.chart.successRate');

    return {
      textStyle: { color: c.text },
      tooltip: { trigger: 'axis' },
      legend: { data: [readLabel, writeLabel, rateLabel], bottom: 0, textStyle: { color: c.text } },
      grid: { left: 60, right: 50, top: 30, bottom: 40 },
      xAxis: {
        type: 'category',
        data: trend.map((p) => p.date),
        axisLabel: { color: c.text },
        axisLine: { lineStyle: { color: c.split } },
      },
      yAxis: [
        { type: 'value', axisLabel: { color: c.text }, splitLine: { lineStyle: { color: c.split } } },
        { type: 'value', max: 100, axisLabel: { color: c.text, formatter: '{value}%' }, splitLine: { show: false } },
      ],
      series: [
        { name: readLabel,  type: 'bar', itemStyle: { color: '#6366f1', borderRadius: [4, 4, 0, 0] }, data: trend.map((p) => p.rowsRead) },
        { name: writeLabel, type: 'bar', itemStyle: { color: '#22c55e', borderRadius: [4, 4, 0, 0] }, data: trend.map((p) => p.rowsWritten) },
        { name: rateLabel,  type: 'line', yAxisIndex: 1, smooth: true, itemStyle: { color: '#f59e0b' }, data: trend.map((p) => p.successRate) },
      ],
    };
  });

  // ── Graphique 2 : répartition des lignes par table ─────────
  readonly tableBreakdownOptions = computed<EChartsCoreOption>(() => {
    const c = this.palette();
    const rows = this.kpiData()?.rowsByTable ?? {};
    const data = Object.entries(rows)
      .map(([name, value]) => ({ name, value }))
      .sort((a, b) => b.value - a.value)
      .slice(0, 8);                       // top 8 tables, lisibilité

    return {
      tooltip: { trigger: 'item', formatter: '{b} : {c} ({d}%)' },
      legend: { bottom: 0, textStyle: { color: c.text } },
      series: [{
        name: this.t('dashboard.chart.rowsByTable'),
        type: 'pie',
        radius: ['45%', '70%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: c.surface, borderWidth: 2 },
        label: { show: false },
        data,
        color: ['#6366f1', '#22c55e', '#f59e0b', '#ec4899', '#06b6d4', '#8b5cf6', '#f97316', '#64748b'],
      }],
    };
  });

  // ── Helpers d'affichage ────────────────────────────────────
  statusSeverity(status: string): 'success' | 'danger' | 'info' | 'warn' | 'secondary' {
    switch (status) {
      case 'COMPLETED':               return 'success';
      case 'FAILED':                  return 'danger';
      case 'STARTED': case 'STARTING': return 'info';
      case 'STOPPED': case 'STOPPING': case 'ABANDONED': return 'warn';
      default:                        return 'secondary';
    }
  }

  /**
   * Durée en secondes calculée depuis les horodatages (précision ms),
   * car le backend arrondit `durationSeconds` (0 sous la seconde).
   * Repli sur `durationSeconds` si les horodatages manquent (job en cours).
   */
  durationOf(x: { startTime: string | null; endTime: string | null; durationSeconds: number | null }): number | null {
    if (x.startTime && x.endTime) {
      const ms = new Date(x.endTime).getTime() - new Date(x.startTime).getTime();
      if (Number.isFinite(ms) && ms >= 0) return ms / 1000;
    }
    return x.durationSeconds;
  }

  /** Classe de couleur du badge statut (palette sobre, sans vert ni rouge). */
  statusClass(status: string): 'is-done' | 'is-failed' | 'is-running' | 'is-neutral' {
    switch (status) {
      case 'COMPLETED':                 return 'is-done';
      case 'FAILED': case 'ABANDONED':  return 'is-failed';
      case 'STARTED': case 'STARTING':  return 'is-running';
      default:                          return 'is-neutral';
    }
  }

  /** Libellé traduit du statut, repli sur la valeur brute si clé absente. */
  statusLabel(status: string): string {
    this.translate.currentLang();
    const key = `jobs.state.${status}`;
    const label = this.translate.instant(key);
    return label === key ? status : label;
  }

  alertSeverity(severity: string): 'danger' | 'warn' | 'info' {
    switch (severity?.toUpperCase()) {
      case 'CRITICAL': case 'HIGH': return 'danger';
      case 'WARNING': case 'MEDIUM': return 'warn';
      default: return 'info';
    }
  }
}