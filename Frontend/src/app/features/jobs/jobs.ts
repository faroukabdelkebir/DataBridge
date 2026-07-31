import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, NgClass } from '@angular/common';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { SelectButtonModule } from 'primeng/selectbutton';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { FormsModule } from '@angular/forms';

import { MonitoringService } from '../../core/services/monitoring.service';
import { JobExecution, StepExecution } from '../../core/models/monitoring.model';
import { DurationPipe } from '../../shared/pipes/duration.pipe';
import { TooltipModule } from 'primeng/tooltip';

const BATCH_STATUSES = [
  'COMPLETED', 'FAILED', 'STARTED', 'STARTING',
  'STOPPING', 'STOPPED', 'ABANDONED', 'UNKNOWN',
] as const;

@Component({
  selector: 'app-jobs',
  imports: [
    TranslatePipe, TableModule, ButtonModule, DialogModule, TooltipModule,
    SelectButtonModule, SelectModule, InputTextModule, FormsModule,
    DatePipe, DecimalPipe, DurationPipe, NgClass,
  ],
  templateUrl: './jobs.html',
})
export class Jobs {
  private monitoring = inject(MonitoringService);
  private translate = inject(TranslateService);

  readonly windowOptions = [
    { label: '7 j', value: 7 },
    { label: '14 j', value: 14 },
    { label: '30 j', value: 30 },
  ];
  days = 7;

  // Source complète (hydratée) + filtres réactifs
  private readonly allJobs = signal<JobExecution[]>([]);
  readonly loading = signal(true);
  readonly search = signal('');
  readonly statusFilter = signal<string | null>(null);

  /** Options du filtre statut (recalculées à la langue). */
  readonly statusOptions = computed(() => {
    this.translate.currentLang();
    return [
      { label: this.translate.instant('jobs.allStatuses'), value: null },
      ...BATCH_STATUSES.map((s) => ({ label: this.statusLabel(s), value: s })),
    ];
  });

  /** Liste filtrée affichée dans le tableau (et exportée en CSV). */
  readonly jobs = computed<JobExecution[]>(() => {
    const term = this.search().trim().toLowerCase();
    const status = this.statusFilter();
    return this.allJobs().filter((j) => {
      const matchTerm = !term || j.jobName.toLowerCase().includes(term);
      const matchStatus = !status || j.status === status;
      return matchTerm && matchStatus;
    });
  });

  // Drill-down steps
  readonly selectedJob = signal<JobExecution | null>(null);
  readonly steps = signal<StepExecution[]>([]);
  readonly stepsLoading = signal(false);
  readonly dialogVisible = signal(false);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    // Source hydratée (/jobs) : compteurs read/write/skip agrégés au niveau job.
    // On récupère large puis on filtre sur la fenêtre de jours choisie.
    this.monitoring.getRecentJobs(1000).subscribe({
      next: (jobs) => {
        const cutoff = Date.now() - this.days * 86_400_000;
        this.allJobs.set(
          jobs.filter((j) => j.startTime && new Date(j.startTime).getTime() >= cutoff),
        );
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  openSteps(job: JobExecution): void {
    this.selectedJob.set(job);
    this.steps.set([]);
    this.stepsLoading.set(true);
    this.dialogVisible.set(true);
    this.monitoring.getJobSteps(job.jobExecutionId).subscribe({
      next: (steps) => { this.steps.set(steps); this.stepsLoading.set(false); },
      error: () => this.stepsLoading.set(false),
    });
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

  /** Classe de couleur du badge (palette sobre, sans vert ni rouge). */
  statusClass(status: string): 'is-done' | 'is-failed' | 'is-running' | 'is-neutral' {
    switch (status) {
      case 'COMPLETED':                 return 'is-done';
      case 'FAILED': case 'ABANDONED':  return 'is-failed';
      case 'STARTED': case 'STARTING':  return 'is-running';
      default:                          return 'is-neutral';
    }
  }

  /** Libellé traduit du statut, repli sur la valeur brute. */
  statusLabel(status: string): string {
    this.translate.currentLang();
    const key = `jobs.state.${status}`;
    const label = this.translate.instant(key);
    return label === key ? status : label;
  }

  /** Export CSV de la liste filtrée courante. */
  exportCsv(): void {
    const rows = this.jobs();
    const header = ['Job', 'Statut', 'Début', 'Durée (s)', 'Lues', 'Écrites', 'Ignorées'];
    const body = rows.map((j) => [
      j.jobName,
      j.status,
      j.startTime ?? '',
      j.durationSeconds ?? '',
      j.totalReadCount,
      j.totalWriteCount,
      j.totalSkipCount,
    ]);
    const csv = [header, ...body]
      .map((r) => r.map(csvCell).join(','))
      .join('\r\n');

    const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `historique-jobs-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }
}

/** Échappe une valeur pour le CSV (guillemets si , " ou saut de ligne). */
function csvCell(value: unknown): string {
  const s = String(value ?? '');
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}
